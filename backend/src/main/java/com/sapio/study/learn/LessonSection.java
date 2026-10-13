package com.sapio.study.learn;

/** @param kind CONCEPT | EXAMPLE | ANALOGY | SUMMARY */
public record LessonSection(int index, String kind, String heading, String body) {
}
