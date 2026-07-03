package com.plantops.testsupport;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CI guard (TODO-28): every {@link SpecRef} points to a real AC-* id from §8.
 * Coverage of all AC rows is incremental (TODO-01).
 */
class SpecRefCoverageTest {

    private static final Pattern AC_ID = Pattern.compile("\\b(AC(?:-[A-Z0-9]+)+)\\b");

    @Test
    void specRefsReferenceKnownAcceptanceIds() throws Exception {
        Set<String> known = loadAcceptanceIds();
        assertFalse(known.isEmpty(), "acceptance catalog must not be empty");

        List<String> violations = new ArrayList<>();
        for (Class<?> testClass : findAnnotatedTestClasses()) {
            collectRefs(testClass, null, known, violations);
            for (Method method : testClass.getDeclaredMethods()) {
                collectRefs(testClass, method, known, violations);
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    private static void collectRefs(
            Class<?> testClass,
            Method method,
            Set<String> known,
            List<String> violations) {
        SpecRef specRef = method != null
                ? method.getAnnotation(SpecRef.class)
                : testClass.getAnnotation(SpecRef.class);
        if (specRef == null) {
            return;
        }
        String target = method != null ? testClass.getSimpleName() + "#" + method.getName()
                : testClass.getSimpleName();
        for (String acId : specRef.value()) {
            if (!known.contains(acId)) {
                violations.add(target + " references unknown " + acId);
            }
        }
    }

    private static Set<String> loadAcceptanceIds() throws IOException {
        Path acceptance = Path.of("docs/sdd/core/08-acceptance.md");
        String text = Files.readString(acceptance);
        Set<String> ids = new LinkedHashSet<>();
        Matcher matcher = AC_ID.matcher(text);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    private static List<Class<?>> findAnnotatedTestClasses() throws Exception {
        Path testRoot = Path.of("src/test/java");
        List<Class<?>> classes = new ArrayList<>();
        try (var stream = Files.walk(testRoot)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        String className = toClassName(testRoot, path);
                        try {
                            Class<?> type = Class.forName(className);
                            if (type.getAnnotation(SpecRef.class) != null
                                    || hasMethodSpecRef(type)) {
                                classes.add(type);
                            }
                        } catch (ClassNotFoundException ignored) {
                            // skip
                        }
                    });
        }
        return classes;
    }

    private static boolean hasMethodSpecRef(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getAnnotation(SpecRef.class) != null) {
                return true;
            }
        }
        return false;
    }

    private static String toClassName(Path root, Path file) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        return relative.substring(0, relative.length() - 5).replace('/', '.');
    }
}
