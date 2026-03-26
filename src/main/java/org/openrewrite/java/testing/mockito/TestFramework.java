/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.mockito;

import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;

enum TestFramework {
    JUNIT4("Before", "After", "org.junit", "junit-4", "", true),
    JUNIT5("BeforeEach", "AfterEach", "org.junit.jupiter.api", "junit-jupiter-api-5", "", false),
    TESTNG("BeforeMethod", "AfterMethod", "org.testng.annotations", "testng-7", "(alwaysRun = true)", false);

    final String setUpAnnotation;
    final String tearDownAnnotation;
    final String setUpAnnotationSignature;
    final String tearDownAnnotationSignature;
    final String classpathResource;
    final String setUpImport;
    final String tearDownImport;
    final String tearDownAnnotationParameters;
    final boolean publicMethods;

    TestFramework(String setUpName, String tearDownName, String annotationPackage,
                  String classpathResource, String tearDownAnnotationParameters, boolean publicMethods) {
        this.setUpAnnotation = "@" + setUpName;
        this.tearDownAnnotation = "@" + tearDownName;
        this.setUpAnnotationSignature = "@" + annotationPackage + "." + setUpName;
        this.tearDownAnnotationSignature = "@" + annotationPackage + "." + tearDownName;
        this.classpathResource = classpathResource;
        this.setUpImport = annotationPackage + "." + setUpName;
        this.tearDownImport = annotationPackage + "." + tearDownName;
        this.tearDownAnnotationParameters = tearDownAnnotationParameters;
        this.publicMethods = publicMethods;
    }

    static TestFramework detect(J tree) {
        if (!FindAnnotations.find(tree, "@org.testng.annotations.Test").isEmpty()) {
            return TESTNG;
        }
        if (!FindAnnotations.find(tree, "@org.junit.Test").isEmpty()) {
            return JUNIT4;
        }
        return JUNIT5;
    }
}
