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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.json.tree.JsonKey;
import org.openrewrite.json.tree.JsonRightPadded;
import org.openrewrite.json.tree.JsonValue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;

public class MigrateStubMappingUuidToId extends Recipe {

    private static final String UUID_KEY = "uuid";
    private static final String ID_KEY = "id";

    private static final Pattern UUID_VALUE = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Getter
    final String displayName = "Migrate the `uuid` field in WireMock stub mapping files to `id`";

    @Getter
    final String description = "WireMock 3 serialized a stub mapping's identifier as both `id` and `uuid`, but 4.x " +
            "dropped the redundant `uuid` field. A stub that carries only `uuid` still parses under 4.x, silently " +
            "getting a randomly generated identifier instead, which breaks anything addressing the stub by id such " +
            "as `removeStub`, `editStub` or `PUT /__admin/mappings/{id}`. Rename `uuid` to `id`, or drop it where an " +
            "`id` is already present. Only stub mapping files are considered, meaning JSON below a `mappings` " +
            "directory holding an object with a `request` or `response` member and a `uuid` holding a UUID.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles("**/mappings/**/*.json"), new JsonIsoVisitor<ExecutionContext>() {

            @Override
            public Json.JsonObject visitObject(Json.JsonObject obj, ExecutionContext ctx) {
                Json.JsonObject o = super.visitObject(obj, ctx);

                List<Json> members = o.getMembers();
                if (!isStubMapping(members)) {
                    return o;
                }

                if (member(members, ID_KEY) == null) {
                    return o.withMembers(ListUtils.map(members, member ->
                            UUID_KEY.equals(keyName(member)) ? renameToId((Json.Member) member) : member));
                }

                // `id` already carries the identifier, so `uuid` is the redundant copy WireMock 4 dropped
                return removeMember(o, members.indexOf(member(members, UUID_KEY)));
            }

            /**
             * Dropping a member has to hand its surrounding whitespace to a neighbour, otherwise removing the
             * first one takes the newline and indent that positioned it, and removing the last one takes the
             * newline that put the closing brace on its own line.
             */
            private Json.JsonObject removeMember(Json.JsonObject o, int index) {
                List<JsonRightPadded<Json>> members = o.getPadding().getMembers();
                if (members.size() == 1) {
                    return o.getPadding().withMembers(emptyList());
                }
                JsonRightPadded<Json> removed = members.get(index);
                List<JsonRightPadded<Json>> remaining = new ArrayList<>(members);
                remaining.remove(index);
                if (index == 0) {
                    remaining.set(0, remaining.get(0).withElement(
                            remaining.get(0).getElement().withPrefix(removed.getElement().getPrefix())));
                } else if (index == members.size() - 1) {
                    int last = remaining.size() - 1;
                    remaining.set(last, remaining.get(last).withAfter(removed.getAfter()));
                }
                return o.getPadding().withMembers(remaining);
            }

            /**
             * A `uuid` on its own is far too common to key off, so only rewrite objects shaped like a stub mapping.
             */
            private boolean isStubMapping(List<Json> members) {
                Json.Member uuid = member(members, UUID_KEY);
                if (uuid == null) {
                    return false;
                }
                JsonValue value = uuid.getValue();
                if (!(value instanceof Json.Literal) ||
                        !UUID_VALUE.matcher(String.valueOf(((Json.Literal) value).getValue())).matches()) {
                    return false;
                }
                return member(members, "request") != null || member(members, "response") != null;
            }

            private Json.Member renameToId(Json.Member member) {
                Json.Literal key = (Json.Literal) member.getKey();
                char quote = key.getSource().isEmpty() ? 0 : key.getSource().charAt(0);
                String source = quote == '"' || quote == '\'' ? quote + ID_KEY + quote : ID_KEY;
                return member.withKey(key.withValue(ID_KEY).withSource(source));
            }

            private Json.@Nullable Member member(List<Json> members, String name) {
                for (Json member : members) {
                    if (name.equals(keyName(member))) {
                        return (Json.Member) member;
                    }
                }
                return null;
            }

            private @Nullable String keyName(Json member) {
                if (!(member instanceof Json.Member)) {
                    return null;
                }
                JsonKey key = ((Json.Member) member).getKey();
                if (!(key instanceof Json.Literal)) {
                    return null;
                }
                Object value = ((Json.Literal) key).getValue();
                return value == null ? null : value.toString();
            }
        });
    }
}
