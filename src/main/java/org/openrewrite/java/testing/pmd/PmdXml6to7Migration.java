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
package org.openrewrite.java.testing.pmd;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PmdXml6to7Migration extends Recipe {

    private static final String PREFIX = "category/java/";

    /**
     * Java rules removed in PMD 7 that have a single unambiguous successor, mapped to that successor.
     * Note that a rule's replacement often lives in a different category than the rule it replaces.
     *
     * @see <a href="https://docs.pmd-code.org/pmd-doc-7.0.0/pmd_release_notes_pmd7.html">PMD 7.0.0 release notes, "Removed Rules"</a>
     */
    private static final Map<String, String> REPLACED_RULES = new LinkedHashMap<>();

    /**
     * Java rules removed in PMD 7 with no successor. PMD 7 fails on a `ref` it cannot resolve, so these are
     * dropped from the ruleset rather than left behind.
     */
    private static final Set<String> REMOVED_RULES = new HashSet<>(Arrays.asList(
            PREFIX + "codestyle.xml/AvoidFinalLocalVariable",
            PREFIX + "performance.xml/AvoidUsingShortType",
            PREFIX + "errorprone.xml/CloneThrowsCloneNotSupportedException",
            PREFIX + "performance.xml/SimplifyStartsWith"));

    static {
        replaced("codestyle.xml/AbstractNaming", "codestyle.xml/ClassNamingConventions");
        replaced("codestyle.xml/AvoidPrefixingMethodParameters", "codestyle.xml/FormalParameterNamingConventions");
        replaced("errorprone.xml/BadComparison", "errorprone.xml/ComparisonWithNaN");
        replaced("errorprone.xml/BeanMembersShouldSerialize", "errorprone.xml/NonSerializableClass");
        replaced("errorprone.xml/DataflowAnomalyAnalysis", "bestpractices.xml/UnusedAssignment");
        replaced("codestyle.xml/DefaultPackage", "codestyle.xml/CommentDefaultAccessModifier");
        replaced("errorprone.xml/DoNotCallSystemExit", "errorprone.xml/DoNotTerminateVM");
        replaced("codestyle.xml/DontImportJavaLang", "codestyle.xml/UnnecessaryImport");
        replaced("errorprone.xml/DontImportSun", "errorprone.xml/UnsupportedJdkApiUsage");
        replaced("codestyle.xml/DuplicateImports", "codestyle.xml/UnnecessaryImport");
        replaced("errorprone.xml/EmptyFinallyBlock", "codestyle.xml/EmptyControlStatement");
        replaced("errorprone.xml/EmptyIfStmt", "codestyle.xml/EmptyControlStatement");
        replaced("errorprone.xml/EmptyInitializer", "codestyle.xml/EmptyControlStatement");
        replaced("errorprone.xml/EmptyStatementBlock", "codestyle.xml/EmptyControlStatement");
        replaced("errorprone.xml/EmptyStatementNotInLoop", "codestyle.xml/UnnecessarySemicolon");
        replaced("errorprone.xml/EmptySwitchStatements", "codestyle.xml/EmptyControlStatement");
        replaced("errorprone.xml/EmptySynchronizedBlock", "codestyle.xml/EmptyControlStatement");
        replaced("errorprone.xml/EmptyTryBlock", "codestyle.xml/EmptyControlStatement");
        replaced("errorprone.xml/EmptyWhileStmt", "codestyle.xml/EmptyControlStatement");
        replaced("design.xml/ExcessiveClassLength", "design.xml/NcssCount");
        replaced("design.xml/ExcessiveMethodLength", "design.xml/NcssCount");
        replaced("codestyle.xml/ForLoopsMustUseBraces", "codestyle.xml/ControlStatementBraces");
        replaced("codestyle.xml/IfElseStmtsMustUseBraces", "codestyle.xml/ControlStatementBraces");
        replaced("codestyle.xml/IfStmtsMustUseBraces", "codestyle.xml/ControlStatementBraces");
        replaced("errorprone.xml/ImportFromSamePackage", "codestyle.xml/UnnecessaryImport");
        replaced("errorprone.xml/InvalidSlf4jMessageFormat", "errorprone.xml/InvalidLogMessageFormat");
        replaced("errorprone.xml/LoggerIsNotStaticFinal", "errorprone.xml/ProperLogger");
        replaced("errorprone.xml/MissingBreakInSwitch", "errorprone.xml/ImplicitSwitchFallThrough");
        replaced("design.xml/ModifiedCyclomaticComplexity", "design.xml/CyclomaticComplexity");
        replaced("design.xml/NcssConstructorCount", "design.xml/NcssCount");
        replaced("design.xml/NcssMethodCount", "design.xml/NcssCount");
        replaced("design.xml/NcssTypeCount", "design.xml/NcssCount");
        replaced("bestpractices.xml/PositionLiteralsFirstInCaseInsensitiveComparisons", "bestpractices.xml/LiteralsFirstInComparisons");
        replaced("bestpractices.xml/PositionLiteralsFirstInComparisons", "bestpractices.xml/LiteralsFirstInComparisons");
        replaced("errorprone.xml/ReturnEmptyArrayRatherThanNull", "errorprone.xml/ReturnEmptyCollectionRatherThanNull");
        replaced("design.xml/SimplifyBooleanAssertion", "bestpractices.xml/SimplifiableTestAssertion");
        replaced("design.xml/StdCyclomaticComplexity", "design.xml/CyclomaticComplexity");
        replaced("codestyle.xml/SuspiciousConstantFieldName", "codestyle.xml/FieldNamingConventions");
        replaced("performance.xml/UnnecessaryWrapperObjectCreation", "codestyle.xml/UnnecessaryBoxing");
        replaced("multithreading.xml/UnsynchronizedStaticDateFormatter", "multithreading.xml/UnsynchronizedStaticFormatter");
        replaced("bestpractices.xml/UnusedImports", "codestyle.xml/UnnecessaryImport");
        replaced("bestpractices.xml/UseAssertEqualsInsteadOfAssertTrue", "bestpractices.xml/SimplifiableTestAssertion");
        replaced("bestpractices.xml/UseAssertNullInsteadOfAssertEquals", "bestpractices.xml/SimplifiableTestAssertion");
        replaced("bestpractices.xml/UseAssertSameInsteadOfAssertEquals", "bestpractices.xml/SimplifiableTestAssertion");
        replaced("bestpractices.xml/UseAssertTrueInsteadOfAssertEquals", "bestpractices.xml/SimplifiableTestAssertion");
        replaced("codestyle.xml/WhileLoopsMustUseBraces", "codestyle.xml/ControlStatementBraces");

        // Deliberately not mapped, because PMD 7 splits each of these across several rules and picking one
        // for the reader would silently change what the ruleset covers. They are left in place for a human:
        //   performance.xml/{Boolean,Byte,Integer,Long,Short}Instantiation
        //       -> codestyle.xml/UnnecessaryBoxing and bestpractices.xml/PrimitiveWrapperInstantiation
        //   codestyle.xml/MIsLeadingVariableName, codestyle.xml/VariableNamingConventions
        //       -> codestyle.xml/{Field,FormalParameter,LocalVariable}NamingConventions
    }

    private static void replaced(String pmd6Rule, String pmd7Rule) {
        REPLACED_RULES.put(PREFIX + pmd6Rule, PREFIX + pmd7Rule);
    }

    @Override
    public String getDisplayName() {
        return "Migrate PMD 6 rulesets to PMD 7";
    }

    @Override
    public String getDescription() {
        return "Update references to Java rules that were removed in PMD 7 in a PMD `ruleset` XML file. Rules " +
               "with a single PMD 7 successor are repointed at it, rules removed without a successor are dropped, " +
               "and a successor that the ruleset already selects is not added twice. Rules that PMD 7 splits " +
               "across several successors are left alone, since choosing one would change what the ruleset covers.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                // A PMD ruleset is always a `ruleset` document; skip any other XML rather than matching a
                // `ruleset` tag that happens to appear somewhere else.
                if (!"ruleset".equals(document.getRoot().getName())) {
                    return document;
                }
                return super.visitDocument(document, ctx);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (!"ruleset".equals(t.getName()) || t.getContent() == null) {
                    return t;
                }

                List<Content> content = new ArrayList<>(t.getContent());
                boolean changed = false;
                for (int i = content.size() - 1; i >= 0; i--) {
                    Xml.Tag rule = asRule(content.get(i));
                    String ref = rule == null ? null : ref(rule);
                    if (ref == null) {
                        continue;
                    }
                    if (REMOVED_RULES.contains(ref)) {
                        content.remove(i);
                        changed = true;
                        continue;
                    }
                    String replacement = REPLACED_RULES.get(ref);
                    if (replacement == null) {
                        continue;
                    }
                    content.remove(i);
                    changed = true;
                    if (!selects(content, replacement)) {
                        insertSorted(content, withRef(rule, replacement));
                    }
                }
                return changed ? t.withContent(content) : t;
            }

            /**
             * Insert a rule at the position that keeps it in `ref` order relative to the rules already present,
             * taking the indentation of whichever rule it is placed next to. Rules that are not being replaced
             * are never reordered, so a ruleset that happens to be unsorted stays as its author wrote it.
             */
            private void insertSorted(List<Content> content, Xml.Tag rule) {
                int lastRule = -1;
                for (int i = 0; i < content.size(); i++) {
                    Xml.Tag existing = asRule(content.get(i));
                    if (existing == null) {
                        continue;
                    }
                    String existingRef = ref(existing);
                    if (existingRef != null && existingRef.compareTo(ref(rule)) > 0) {
                        content.add(i, rule.withPrefix(existing.getPrefix()));
                        return;
                    }
                    lastRule = i;
                }
                if (lastRule == -1) {
                    content.add(rule);
                } else {
                    content.add(lastRule + 1, rule.withPrefix(content.get(lastRule).getPrefix()));
                }
            }

            private boolean selects(List<Content> content, String ref) {
                for (Content c : content) {
                    Xml.Tag rule = asRule(c);
                    if (rule != null && ref.equals(ref(rule))) {
                        return true;
                    }
                }
                return false;
            }

            private Xml.@Nullable Tag asRule(Content content) {
                if (content instanceof Xml.Tag && "rule".equals(((Xml.Tag) content).getName())) {
                    return (Xml.Tag) content;
                }
                return null;
            }

            private @Nullable String ref(Xml.Tag rule) {
                for (Xml.Attribute attribute : rule.getAttributes()) {
                    if ("ref".equals(attribute.getKeyAsString())) {
                        return attribute.getValueAsString();
                    }
                }
                return null;
            }

            private Xml.Tag withRef(Xml.Tag rule, String ref) {
                List<Xml.Attribute> attributes = new ArrayList<>(rule.getAttributes());
                for (int i = 0; i < attributes.size(); i++) {
                    Xml.Attribute attribute = attributes.get(i);
                    if ("ref".equals(attribute.getKeyAsString())) {
                        attributes.set(i, attribute.withValue(attribute.getValue().withValue(ref)));
                    }
                }
                return rule.withAttributes(attributes);
            }
        };
    }
}
