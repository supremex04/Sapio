package com.ved.study.question;

import com.ved.common.error.ApplicationException;
import com.ved.common.error.ErrorCode;
import com.ved.knowledge.retrieval.ContextAssembler;
import com.ved.study.ai.StudyAi;
import org.springframework.stereotype.Component;

/**
 * Grades answers: choice questions deterministically, short answers with the AI against the reference
 * answer and the question's source chunks. Never persists anything.
 */
@Component
public class AnswerGrader {

    private final StudyAi ai;

    AnswerGrader(StudyAi ai) {
        this.ai = ai;
    }

    public record Graded(Verdict verdict, double score, String feedback, Integer selectedOption, String answerText) {
    }

    public Graded grade(StudyQuestion question, AnswerRequest answer, String language,
                        ContextAssembler.Context sources) {
        if (question.isChoice()) {
            Integer option = answer.optionIndex();
            if (option == null || option < 0 || option >= question.getOptions().size()) {
                throw new ApplicationException(ErrorCode.VALIDATION_FAILED,
                        "optionIndex must be between 0 and " + (question.getOptions().size() - 1));
            }
            boolean correct = option.equals(question.getCorrectOption());
            return new Graded(correct ? Verdict.CORRECT : Verdict.INCORRECT, correct ? 1.0 : 0.0,
                    question.getExplanation(), option, null);
        }
        String text = answer.text() == null ? "" : answer.text().strip();
        if (text.isEmpty()) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, "text is required for this question");
        }
        StudyAi.GradeResult result = ai.grade(new StudyAi.GradeSpec(language,
                new StudyAi.QuestionToGrade(question.getPrompt(), question.getReferenceAnswer(),
                        question.getExplanation()),
                StudyAi.sources(sources.sections()), text));
        return new Graded(result.verdict(), result.score(), result.feedback(), null, text);
    }
}
