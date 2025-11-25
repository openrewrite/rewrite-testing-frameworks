import {RecipeRegistry} from "@openrewrite/rewrite";
import {JestToVitest} from "./jest-to-vitest";

export function activate(registry: RecipeRegistry) {
    registry.register(JestToVitest);
}

export {JestToVitest} from "./jest-to-vitest";
