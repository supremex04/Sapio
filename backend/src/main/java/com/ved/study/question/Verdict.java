package com.ved.study.question;

/** Outcome of an answer. Score bands: CORRECT 0.8-1, PARTIAL 0.3-0.7, INCORRECT 0-0.2. */
public enum Verdict {
    CORRECT,
    PARTIAL,
    INCORRECT;

    public boolean isCorrect() {
        return this == CORRECT;
    }
}
