/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.logging.api.internal.format;

import static org.junit.jupiter.api.Assertions.*;

import com.swirlds.logging.util.Throwables;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class StackTracePrinterTest {

    public static final int DEPTH = 100;

    @Test
    void printShouldBehaveAsPrintStackTrace() throws IOException {
        // Given
        final StringBuilder writer = new StringBuilder();
        final Throwable deepThrowable = Throwables.createDeepThrowable(DEPTH);
        // When
        StackTracePrinter.print(writer, deepThrowable);
        // Then
        final StringWriter stringWriter = new StringWriter();
        deepThrowable.printStackTrace(new PrintWriter(stringWriter));
        assertEquals(stringWriter.toString(), writer.toString());
    }

    @Test
    void printCircularReferenceShouldBehaveAsPrintStackTrace() throws IOException {
        // Given
        final StringBuilder writer = new StringBuilder();
        final Throwable deepThrowable0 = Throwables.createDeepThrowable(DEPTH);
        final Throwable deepThrowable1 = new Throwable("1", deepThrowable0);
        final Throwable deepThrowable2 = new Throwable("2", deepThrowable1);
        final Throwable deepThrowable3 = new Throwable("3", deepThrowable2);
        deepThrowable0.initCause(deepThrowable3);

        // When
        StackTracePrinter.print(writer, deepThrowable3);
        writer.append(System.lineSeparator());

        // Then
        final StringWriter stringWriter = new StringWriter();
        deepThrowable3.printStackTrace(new PrintWriter(stringWriter));
        assertEquals(stringWriter.toString(), writer.toString());
    }

    @Test
    void printWithThrowableWithDeepCauseShouldContainTraceAndCause() throws IOException {
        final StringBuilder writer = new StringBuilder();
        StackTracePrinter.print(writer, Throwables.createThrowableWithDeepCause(DEPTH, DEPTH));
        final String stackTrace = writer.toString();
        assertTrue(stackTrace.contains("java.lang.RuntimeException: test\n"));
        assertTrue(stackTrace.contains("Caused by: java.lang.RuntimeException: test\n"));
        int count = countMatches(
                stackTrace,
                "\\tat com\\.swirlds\\.logging\\.util\\.Throwables\\.createThrowableWithDeepCause\\(Throwables\\.java:\\d+\\)");

        assertEquals(DEPTH + 2, count);
        count = countMatches(
                stackTrace,
                "\\tat com\\.swirlds\\.logging\\.util\\.Throwables\\.createDeepThrowable\\(Throwables\\.java:\\d+\\)");
        assertEquals(DEPTH + 1, count);
        count = countMatches(stackTrace, "\\.\\.\\. \\d+ more");
        assertEquals(1, count);
    }

    private static int countMatches(final String stackTrace, final String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(stackTrace);

        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
