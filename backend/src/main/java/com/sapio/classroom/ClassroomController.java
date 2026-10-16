package com.sapio.classroom;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.sapio.common.error.ApplicationException;
import com.sapio.common.error.ErrorCode;
import com.sapio.study.assess.AssessmentDifficulty;
import com.sapio.study.assess.AssessmentResponse;
import com.sapio.security.CurrentUserId;
import com.sapio.user.Role;
import com.sapio.user.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Classroom API (docs/specs/classroom.md). Teacher endpoints refuse other roles (403). */
@RestController
@RequestMapping("/api/classroom")
class ClassroomController {

    private final RosterService roster;
    private final ClassShareService shares;
    private final ClassTestService tests;
    private final InsightsService insights;
    private final BulletinService bulletin;
    private final UserService users;

    ClassroomController(RosterService roster, ClassShareService shares, ClassTestService tests,
                        InsightsService insights, BulletinService bulletin, UserService users) {
        this.roster = roster;
        this.shares = shares;
        this.tests = tests;
        this.insights = insights;
        this.bulletin = bulletin;
        this.users = users;
    }

    record InviteRequest(@NotNull @Size(min = 1, max = RosterService.MAX_INVITES) List<String> emails) {
    }

    record StudentsRequest(List<UUID> studentIds) {
    }

    record CreateTestRequest(@Size(max = 200) String title, @NotNull ClassTestService.Format format,
                             @NotNull AssessmentDifficulty difficulty, @Min(3) @Max(30) int questionCount) {
    }

    record UpdateTestRequest(@Size(max = 200) String title, Instant dueAt) {
    }

    record SendRequest(List<UUID> studentIds, Instant dueAt) {
    }

    record NoticeRequest(String title, String body) {
    }

    // ------------------------------------------------------------------------------- teacher: class at a glance

    @GetMapping("/overview")
    InsightsService.Overview overview(@CurrentUserId UUID userId) {
        return insights.overview(userId);
    }

    // ------------------------------------------------------------------------------- teacher: students

    @GetMapping("/students")
    List<RosterService.Member> roster(@CurrentUserId UUID userId) {
        return roster.roster(userId);
    }

    @PostMapping("/students/invite")
    List<RosterService.InviteResult> invite(@CurrentUserId UUID userId, @Valid @RequestBody InviteRequest body) {
        return roster.invite(userId, body.emails());
    }

    @DeleteMapping("/students/members/{membershipId}")
    ResponseEntity<Void> remove(@CurrentUserId UUID userId, @PathVariable UUID membershipId) {
        roster.remove(userId, membershipId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/students/{studentId}")
    InsightsService.StudentProfile student(@CurrentUserId UUID userId, @PathVariable UUID studentId) {
        return insights.student(userId, studentId);
    }

    @GetMapping("/assessments/{assessmentId}")
    AssessmentResponse studentAssessment(@CurrentUserId UUID userId, @PathVariable UUID assessmentId) {
        return insights.assessment(userId, assessmentId);
    }

    // ------------------------------------------------------------------------------- teacher: documents

    @GetMapping("/documents/stats")
    List<ClassShareService.DocumentStats> documentStats(@CurrentUserId UUID userId) {
        return shares.stats(userId);
    }

    @PostMapping("/documents/{documentId}/share")
    ClassShareService.ShareResult share(@CurrentUserId UUID userId, @PathVariable UUID documentId,
                                        @RequestBody(required = false) StudentsRequest body) {
        return shares.share(userId, documentId, body == null ? List.of() : body.studentIds());
    }

    @GetMapping("/documents/{documentId}/students")
    List<InsightsService.DocumentProgress> documentStudents(@CurrentUserId UUID userId, @PathVariable UUID documentId) {
        return insights.documentStudents(userId, documentId);
    }

    // ------------------------------------------------------------------------------- teacher: class tests

    @GetMapping("/tests")
    List<ClassTestService.Summary> tests(@CurrentUserId UUID userId, @RequestParam(required = false) UUID documentId) {
        return tests.list(userId, documentId);
    }

    @PostMapping("/documents/{documentId}/tests")
    ClassTestService.Test createTest(@CurrentUserId UUID userId, @PathVariable UUID documentId,
                                     @Valid @RequestBody CreateTestRequest body) {
        return tests.create(userId, documentId, body.title(), body.format(), body.difficulty(), body.questionCount());
    }

    @GetMapping("/tests/{testId}")
    ClassTestService.Test test(@CurrentUserId UUID userId, @PathVariable UUID testId) {
        return tests.get(userId, testId);
    }

    @PutMapping("/tests/{testId}")
    ClassTestService.Test updateTest(@CurrentUserId UUID userId, @PathVariable UUID testId,
                                     @Valid @RequestBody UpdateTestRequest body) {
        return tests.update(userId, testId, body.title(), body.dueAt());
    }

    @DeleteMapping("/tests/{testId}")
    ResponseEntity<Void> deleteTest(@CurrentUserId UUID userId, @PathVariable UUID testId) {
        tests.delete(userId, testId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tests/{testId}/questions")
    ClassTestService.Test addQuestion(@CurrentUserId UUID userId, @PathVariable UUID testId,
                                      @RequestBody ClassTestService.QuestionInput body) {
        return tests.addQuestion(userId, testId, body);
    }

    @PutMapping("/tests/{testId}/questions/{questionId}")
    ClassTestService.Test updateQuestion(@CurrentUserId UUID userId, @PathVariable UUID testId,
                                         @PathVariable UUID questionId, @RequestBody ClassTestService.QuestionInput body) {
        return tests.updateQuestion(userId, testId, questionId, body);
    }

    @DeleteMapping("/tests/{testId}/questions/{questionId}")
    ClassTestService.Test deleteQuestion(@CurrentUserId UUID userId, @PathVariable UUID testId,
                                         @PathVariable UUID questionId) {
        return tests.deleteQuestion(userId, testId, questionId);
    }

    @PostMapping("/tests/{testId}/send")
    Map<String, Integer> send(@CurrentUserId UUID userId, @PathVariable UUID testId,
                              @RequestBody(required = false) SendRequest body) {
        return Map.of("sent", tests.send(userId, testId, body == null ? List.of() : body.studentIds(),
                body == null ? null : body.dueAt()));
    }

    @GetMapping("/tests/{testId}/results")
    ClassTestService.Results results(@CurrentUserId UUID userId, @PathVariable UUID testId) {
        return tests.results(userId, testId);
    }

    // ------------------------------------------------------------------------------- notices and calendar

    /** A teacher's notices, or a student's notices from their teachers. */
    @GetMapping("/notices")
    List<BulletinService.Notice> notices(@CurrentUserId UUID userId) {
        return isTeacher(userId) ? bulletin.teacherNotices(userId) : bulletin.studentNotices(userId);
    }

    @PostMapping("/notices")
    BulletinService.Notice postNotice(@CurrentUserId UUID userId, @RequestBody NoticeRequest body) {
        return bulletin.postNotice(userId, body.title(), body.body());
    }

    @DeleteMapping("/notices/{noticeId}")
    ResponseEntity<Void> deleteNotice(@CurrentUserId UUID userId, @PathVariable UUID noticeId) {
        bulletin.deleteNotice(userId, noticeId);
        return ResponseEntity.noContent().build();
    }

    /** Calendar entries in [from, to): a teacher's own, or a student's from their teachers (with tests due). */
    @GetMapping("/calendar")
    List<BulletinService.CalendarItem> calendar(@CurrentUserId UUID userId, @RequestParam Instant from,
                                                @RequestParam Instant to) {
        if (!to.isAfter(from) || Duration.between(from, to).toDays() > 400) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, "Ask for at most a year of calendar");
        }
        return isTeacher(userId) ? bulletin.teacherCalendar(userId, from, to) : bulletin.studentCalendar(userId, from, to);
    }

    @PostMapping("/events")
    BulletinService.CalendarItem createEvent(@CurrentUserId UUID userId, @RequestBody BulletinService.EventInput body) {
        return bulletin.createEvent(userId, body);
    }

    @PutMapping("/events/{eventId}")
    BulletinService.CalendarItem updateEvent(@CurrentUserId UUID userId, @PathVariable UUID eventId,
                                             @RequestBody BulletinService.EventInput body) {
        return bulletin.updateEvent(userId, eventId, body);
    }

    @DeleteMapping("/events/{eventId}")
    ResponseEntity<Void> deleteEvent(@CurrentUserId UUID userId, @PathVariable UUID eventId) {
        bulletin.deleteEvent(userId, eventId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------------------- the student's side

    @GetMapping("/invitations")
    List<RosterService.Invitation> invitations(@CurrentUserId UUID userId) {
        return roster.invitations(userId);
    }

    @PostMapping("/invitations/{invitationId}/accept")
    ResponseEntity<Void> accept(@CurrentUserId UUID userId, @PathVariable UUID invitationId) {
        roster.respond(userId, invitationId, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invitations/{invitationId}/decline")
    ResponseEntity<Void> decline(@CurrentUserId UUID userId, @PathVariable UUID invitationId) {
        roster.respond(userId, invitationId, false);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/teachers")
    List<RosterService.Teacher> teachers(@CurrentUserId UUID userId) {
        return roster.teachers(userId);
    }

    @DeleteMapping("/teachers/{teacherId}")
    ResponseEntity<Void> leave(@CurrentUserId UUID userId, @PathVariable UUID teacherId) {
        roster.leave(userId, teacherId);
        return ResponseEntity.noContent().build();
    }

    /** The student opened a document (a shared one shows as opened to the teacher). */
    @PostMapping("/opened/{documentId}")
    ResponseEntity<Void> opened(@CurrentUserId UUID userId, @PathVariable UUID documentId) {
        shares.opened(userId, documentId);
        return ResponseEntity.noContent().build();
    }

    private boolean isTeacher(UUID userId) {
        return users.getById(userId).getRole() == Role.TEACHER;
    }
}
