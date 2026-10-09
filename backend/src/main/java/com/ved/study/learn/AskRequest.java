package com.ved.study.learn;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AskRequest(@NotBlank @Size(max = 2000) String question, @Valid @Size(max = 20) List<Turn> history) {

    public record Turn(@Pattern(regexp = "user|tutor") String role, @NotBlank @Size(max = 4000) String text) {
    }
}
