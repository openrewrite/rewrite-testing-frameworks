import {ExecutionContext, Recipe, TreeVisitor} from "@openrewrite/rewrite";
import {
    capture,
    JavaScriptVisitor,
    JS,
    maybeAddImport,
    maybeRemoveImport,
    pattern,
    rewrite,
    template
} from "@openrewrite/rewrite/javascript";
import {J} from "@openrewrite/rewrite/java";

// Configuration for Jest patterns - for type attribution
// Note: Jest globals are available without imports, so we only specify dependencies
const JEST_CONFIG = {
    dependencies: {
        '@types/jest': '^29.5.13'
    },
};

// Configuration for Vitest templates - for type attribution
// Note: Vitest requires explicit imports
const VITEST_CONFIG = {
    dependencies: {
        'vitest': '^2.0.0'
    },
    context: ['import { vi } from "vitest";',]
};

/**
 * Migrates Jest test files to Vitest.
 *
 * This recipe handles:
 * - Renaming jest imports to vitest
 * - Transforming Jest global functions (describe, it, test, expect, etc.) to use Vitest
 * - Converting Jest mock patterns to Vitest equivalents
 * - Updating jest.fn(), jest.spyOn(), etc. to vi.fn(), vi.spyOn()
 * - Transforming jest.mock() to vi.mock()
 */
export class JestToVitest extends Recipe {
    name = "org.openrewrite.javascript.testing.JestToVitest";
    displayName = "Migrate Jest to Vitest";
    description = "Migrates Jest test files to use Vitest as the testing framework. " +
        "This includes updating imports, transforming global functions, and converting " +
        "Jest-specific mocking patterns to Vitest equivalents.";

    async editor(): Promise<TreeVisitor<any, ExecutionContext>> {
        return new JestToVitestVisitor();
    }
}

/**
 * A transformation rule with its associated import requirements
 */
interface TransformationRule {
    rule: ReturnType<typeof rewrite>;
    imports: string[];  // Vitest members to import when this rule matches
}

// Transformation rules organized by the method/function name being invoked
// The key is the simple name of the method (e.g., 'fn' for jest.fn(), 'describe' for describe())
const transformationRules: Record<string, TransformationRule> = {
    // Jest object method transformations (jest.X() -> vi.X())
    fn: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`jest.fn(${args})`.configure(JEST_CONFIG),
                after: template`vi.fn(${args})`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['vi']
    },
    spyOn: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`jest.spyOn(${args})`.configure(JEST_CONFIG),
                after: template`vi.spyOn(${args})`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['vi']
    },
    mock: {
        rule: rewrite(() => {
            const path = capture('path');
            const literalBody = capture<J.Literal>({
                name: 'literalBody',
                constraint: (node) => node.kind === J.Kind.Literal
            });
            // Match mock with arrow factory that returns a literal value
            // jest.mock('./path', () => 'value') -> vi.mock('./path', () => ({ default: 'value' }))
            return {
                before: pattern`jest.mock(${path}, () => ${literalBody})`.configure(JEST_CONFIG),
                after: template`vi.mock(${path}, () => ({ default: ${literalBody} }))`.configure(VITEST_CONFIG)
            };
        }).orElse(rewrite(() => {
            // Fallback: general mock transformation for other cases (including object literals)
            const args = capture({variadic: true});
            return {
                before: pattern`jest.mock(${args})`.configure(JEST_CONFIG),
                after: template`vi.mock(${args})`.configure(VITEST_CONFIG)
            };
        })),
        imports: ['vi']
    },
    unmock: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`jest.unmock(${args})`.configure(JEST_CONFIG),
                after: template`vi.unmock(${args})`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['vi']
    },
    clearAllMocks: {
        rule: rewrite(() => ({
            before: pattern`jest.clearAllMocks()`.configure(JEST_CONFIG),
            after: template`vi.clearAllMocks()`.configure(VITEST_CONFIG)
        })),
        imports: ['vi']
    },
    resetAllMocks: {
        rule: rewrite(() => ({
            before: pattern`jest.resetAllMocks()`.configure(JEST_CONFIG),
            after: template`vi.resetAllMocks()`.configure(VITEST_CONFIG)
        })),
        imports: ['vi']
    },
    restoreAllMocks: {
        rule: rewrite(() => ({
            before: pattern`jest.restoreAllMocks()`.configure(JEST_CONFIG),
            after: template`vi.restoreAllMocks()`.configure(VITEST_CONFIG)
        })),
        imports: ['vi']
    },
    useFakeTimers: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`jest.useFakeTimers(${args})`.configure(JEST_CONFIG),
                after: template`vi.useFakeTimers(${args})`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['vi']
    },
    useRealTimers: {
        rule: rewrite(() => ({
            before: pattern`jest.useRealTimers()`.configure(JEST_CONFIG),
            after: template`vi.useRealTimers()`.configure(VITEST_CONFIG)
        })),
        imports: ['vi']
    },
    runAllTimers: {
        rule: rewrite(() => ({
            before: pattern`jest.runAllTimers()`.configure(JEST_CONFIG),
            after: template`vi.runAllTimers()`.configure(VITEST_CONFIG)
        })),
        imports: ['vi']
    },
    runOnlyPendingTimers: {
        rule: rewrite(() => ({
            before: pattern`jest.runOnlyPendingTimers()`.configure(JEST_CONFIG),
            after: template`vi.runOnlyPendingTimers()`.configure(VITEST_CONFIG)
        })),
        imports: ['vi']
    },
    advanceTimersByTime: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`jest.advanceTimersByTime(${args})`.configure(JEST_CONFIG),
                after: template`vi.advanceTimersByTime(${args})`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['vi']
    },
    advanceTimersToNextTimer: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`jest.advanceTimersToNextTimer(${args})`.configure(JEST_CONFIG),
                after: template`vi.advanceTimersToNextTimer(${args})`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['vi']
    },
    clearAllTimers: {
        rule: rewrite(() => ({
            before: pattern`jest.clearAllTimers()`.configure(JEST_CONFIG),
            after: template`vi.clearAllTimers()`.configure(VITEST_CONFIG)
        })),
        imports: ['vi']
    },
    getTimerCount: {
        rule: rewrite(() => ({
            before: pattern`jest.getTimerCount()`.configure(JEST_CONFIG),
            after: template`vi.getTimerCount()`.configure(VITEST_CONFIG)
        })),
        imports: ['vi']
    },
    setSystemTime: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`jest.setSystemTime(${args})`.configure(JEST_CONFIG),
                after: template`vi.setSystemTime(${args})`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['vi']
    },
    getRealSystemTime: {
        rule: rewrite(() => ({
            before: pattern`jest.getRealSystemTime()`.configure(JEST_CONFIG),
            after: template`vi.getRealSystemTime()`.configure(VITEST_CONFIG)
        })),
        imports: ['vi']
    },
    mocked: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`jest.mocked(${args})`.configure(JEST_CONFIG),
                after: template`vi.mocked(${args})`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['vi']
    },
    isMockFunction: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`jest.isMockFunction(${args})`.configure(JEST_CONFIG),
                after: template`vi.isMockFunction(${args})`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['vi']
    },
    doMock: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`jest.doMock(${args})`.configure(JEST_CONFIG),
                after: template`vi.doMock(${args})`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['vi']
    },
    doUnmock: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`jest.doUnmock(${args})`.configure(JEST_CONFIG),
                after: template`vi.doUnmock(${args})`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['vi']
    },
    // vi.importActual() is async unlike jest.requireActual()
    // When in async context, we add await; otherwise just rename and let developer handle it
    requireActual: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`jest.requireActual(${args})`.configure(JEST_CONFIG),
                after: template`await vi.importActual(${args})`.configure(VITEST_CONFIG),
                where: (_node, cursor) => {
                    // Check if we're inside an async function or async arrow function
                    const enclosingMethod = cursor.firstEnclosing(
                        (n): n is J.MethodDeclaration => n.kind === J.Kind.MethodDeclaration
                    );
                    if (enclosingMethod?.modifiers?.some(m => m.keyword === 'async')) {
                        return true;
                    }
                    const enclosingArrow = cursor.firstEnclosing(
                        (n): n is JS.ArrowFunction => n.kind === JS.Kind.ArrowFunction
                    );
                    if (enclosingArrow?.modifiers?.some(m => m.keyword === 'async')) {
                        return true;
                    }
                    return false;
                }
            };
        }).orElse(rewrite(() => {
            // Fallback: not in async context, just rename without await
            const args = capture({variadic: true});
            return {
                before: pattern`jest.requireActual(${args})`.configure(JEST_CONFIG),
                after: template`vi.importActual(${args})`.configure(VITEST_CONFIG)
            };
        })),
        imports: ['vi']
    },
    setTimeout: {
        rule: rewrite(() => {
            const timeout = capture('timeout');
            return {
                before: pattern`jest.setTimeout(${timeout})`.configure(JEST_CONFIG),
                after: template`vi.setConfig({ testTimeout: ${timeout} })`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['vi']
    },

    // Jest global function transformations (re-attribute types from Jest to Vitest)
    describe: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`describe(${args})`.configure(JEST_CONFIG),
                after: template`describe(${args})`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['describe']
    },
    it: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`it(${args})`.configure(JEST_CONFIG),
                after: template`it(${args})`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['it']
    },
    test: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`test(${args})`.configure(JEST_CONFIG),
                after: template`test(${args})`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['test']
    },
    expect: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`expect(${args})`.configure(JEST_CONFIG),
                after: template`expect(${args})`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['expect']
    },
    beforeEach: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`beforeEach(${args})`.configure(JEST_CONFIG),
                after: template`beforeEach(${args})`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['beforeEach']
    },
    afterEach: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`afterEach(${args})`.configure(JEST_CONFIG),
                after: template`afterEach(${args})`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['afterEach']
    },
    beforeAll: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`beforeAll(${args})`.configure(JEST_CONFIG),
                after: template`beforeAll(${args})`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['beforeAll']
    },
    afterAll: {
        rule: rewrite(() => {
            const args = capture({variadic: true});
            return {
                before: pattern`afterAll(${args})`.configure(JEST_CONFIG),
                after: template`afterAll(${args})`.configure(VITEST_CONFIG)
            };
        }),
        imports: ['afterAll']
    },
};

class JestToVitestVisitor extends JavaScriptVisitor<ExecutionContext> {
    // Track which imports are needed based on transformations made
    private requiredImports = new Set<string>();

    protected async visitMethodInvocation(
        method: J.MethodInvocation,
        ctx: ExecutionContext
    ): Promise<J | undefined> {
        // Visit children first
        method = await super.visitMethodInvocation(method, ctx) as J.MethodInvocation;

        // Get the simple name of the method being invoked
        const simpleName = method.name.simpleName;

        // Look up the transformation rule by name
        const transformation = transformationRules[simpleName];
        if (transformation) {
            const transformed = await transformation.rule.tryOn(this.cursor, method);
            if (transformed) {
                // Track the imports needed for this transformation
                for (const imp of transformation.imports) {
                    this.requiredImports.add(imp);
                }
                return transformed;
            }
        }

        return method;
    }

    protected async visitJsCompilationUnit(
        cu: JS.CompilationUnit,
        ctx: ExecutionContext
    ): Promise<J | undefined> {
        // Reset state for this file
        this.requiredImports.clear();

        // Remove Jest imports
        maybeRemoveImport(this, 'jest');
        maybeRemoveImport(this, '@jest/globals');

        // Visit the tree to collect required imports
        cu = await super.visitJsCompilationUnit(cu, ctx) as JS.CompilationUnit;

        // Add the required Vitest imports
        for (const member of this.requiredImports) {
            maybeAddImport(this, {module: 'vitest', member});
        }

        return cu;
    }
}
