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
package org.openrewrite.java.testing.wiremock;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.FindSourceFiles;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.json.tree.JsonKey;
import org.openrewrite.json.tree.JsonValue;

import java.util.Iterator;
import java.util.List;

import static java.util.Collections.singletonList;

public class RemoveDuplicateContentTypeStubHeader extends Recipe {

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String HEADERS = "headers";

    @Getter
    final String displayName = "Keep a single `Content-Type` response header in WireMock stub files";

    @Getter
    final String description = "WireMock 3 ran on Jetty 11, which stripped every `Content-Type` response header " +
            "but the last, so a stub file listing several of them served only one. WireMock 4 returns all of them, " +
            "and some clients reject a response carrying more than one. Keep only the last value, which is the one " +
            "WireMock 3 actually sent. Only JSON below a `mappings` directory is considered, and request header " +
            "matchers are untouched, since those hold matcher objects rather than plain strings.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles("**/mappings/**/*.json"), new JsonIsoVisitor<ExecutionContext>() {

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member m = super.visitMember(member, ctx);
                if (!CONTENT_TYPE.equalsIgnoreCase(keyName(m)) || !withinResponseHeaders()) {
                    return m;
                }
                if (!(m.getValue() instanceof Json.Array)) {
                    return m;
                }
                Json.Array values = (Json.Array) m.getValue();
                List<JsonValue> entries = values.getValues();
                if (entries.size() < 2) {
                    return m;
                }
                for (JsonValue entry : entries) {
                    if (!(entry instanceof Json.Literal) ||
                            !(((Json.Literal) entry).getValue() instanceof String)) {
                        return m;
                    }
                }
                // Collapse to the single value WireMock 3 served, keeping the array's own formatting
                JsonValue last = entries.get(entries.size() - 1);
                return m.withValue(values.withValues(singletonList(
                        last.withPrefix(entries.get(0).getPrefix()))));
            }

            /**
             * Only response headers hold bare strings, but scoping to a `headers` object as well keeps this off
             * unrelated JSON that happens to have a `Content-Type` array.
             */
            private boolean withinResponseHeaders() {
                for (Iterator<Object> path = getCursor().getPath(); path.hasNext(); ) {
                    Object parent = path.next();
                    if (parent instanceof Json.Member && HEADERS.equalsIgnoreCase(keyName((Json.Member) parent))) {
                        return true;
                    }
                }
                return false;
            }

            private @Nullable String keyName(Json.Member member) {
                JsonKey key = member.getKey();
                if (!(key instanceof Json.Literal)) {
                    return null;
                }
                Object value = ((Json.Literal) key).getValue();
                return value == null ? null : value.toString();
            }
        });
    }
}
