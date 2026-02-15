import {RecipeSpec} from "@openrewrite/rewrite/test";
import {javascript, npm, packageJson, typescript} from "@openrewrite/rewrite/javascript";
import {JestToVitest} from "../src/jest-to-vitest";
import {withDir} from 'tmp-promise';

describe("JestToVitest", () => {
    const spec = new RecipeSpec();
    spec.recipe = new JestToVitest();

    // Helper function to create tests with proper npm dependencies for type attribution
    async function testWithJestDependency(before: string, after: string, useJavaScript = false) {
        await withDir(async (tmpDir) => {
            const sources = npm(
                tmpDir.path,

                packageJson(JSON.stringify({
                    dependencies: {
                        "jest": "^29.0.0"
                    },
                    devDependencies: {
                        "@types/jest": "^29.5.13"
                    }
                })),

                useJavaScript ? javascript(before, after) : typescript(before, after)
            );

            const sourcesArray = [];
            for await (const source of sources) {
                sourcesArray.push(source);
            }

            return spec.rewriteRun(...sourcesArray);
        }, {unsafeCleanup: true});
    }

    test("transforms jest.fn() to vi.fn()", async () => {
        return testWithJestDependency(
            `const mockFn = jest.fn();`,
            `import {vi} from 'vitest';

const mockFn = vi.fn();`
        );
    });

    test("transforms jest.fn() with implementation to vi.fn()", async () => {
        return testWithJestDependency(
            `const mockFn = jest.fn(() => 'mocked');`,
            `import {vi} from 'vitest';

const mockFn = vi.fn(() => 'mocked');`
        );
    });

    test("transforms jest.spyOn() to vi.spyOn()", async () => {
        return testWithJestDependency(
            `const spy = jest.spyOn(obj, 'method');`,
            `import {vi} from 'vitest';

const spy = vi.spyOn(obj, 'method');`
        );
    });

    test("transforms jest.mock() to vi.mock()", async () => {
        return testWithJestDependency(
            `jest.mock('./module');`,
            `import {vi} from 'vitest';

vi.mock('./module');`
        );
    });

    test("transforms jest.mock() with factory to vi.mock()", async () => {
        return testWithJestDependency(
            `jest.mock('./module', () => ({
    default: jest.fn()
}));`,
            `import {vi} from 'vitest';

vi.mock('./module', () => ({
    default: vi.fn()
}));`
        );
    });

    test("transforms jest.mock() with literal factory to vi.mock() with default export", async () => {
        return testWithJestDependency(
            `jest.mock('./module', () => 'mocked value');`,
            `import {vi} from 'vitest';

vi.mock('./module', () => ({ default: 'mocked value'}));`
        );
    });

    test("transforms jest.unmock() to vi.unmock()", async () => {
        return testWithJestDependency(
            `jest.unmock('./module');`,
            `import {vi} from 'vitest';

vi.unmock('./module');`
        );
    });

    test("transforms jest.clearAllMocks() to vi.clearAllMocks()", async () => {
        return testWithJestDependency(
            `afterEach(() => {
    jest.clearAllMocks();
});`,
            `import {afterEach, vi} from 'vitest';

afterEach(() => {
    vi.clearAllMocks();
});`
        );
    });

    test("transforms jest.resetAllMocks() to vi.resetAllMocks()", async () => {
        return testWithJestDependency(
            `jest.resetAllMocks();`,
            `import {vi} from 'vitest';

vi.resetAllMocks();`
        );
    });

    test("transforms jest.restoreAllMocks() to vi.restoreAllMocks()", async () => {
        return testWithJestDependency(
            `jest.restoreAllMocks();`,
            `import {vi} from 'vitest';

vi.restoreAllMocks();`
        );
    });

    test("transforms jest.useFakeTimers() to vi.useFakeTimers()", async () => {
        return testWithJestDependency(
            `beforeEach(() => {
    jest.useFakeTimers();
});`,
            `import {beforeEach, vi} from 'vitest';

beforeEach(() => {
    vi.useFakeTimers();
});`
        );
    });

    test("transforms jest.useRealTimers() to vi.useRealTimers()", async () => {
        return testWithJestDependency(
            `afterEach(() => {
    jest.useRealTimers();
});`,
            `import {afterEach, vi} from 'vitest';

afterEach(() => {
    vi.useRealTimers();
});`
        );
    });

    test("transforms jest.runAllTimers() to vi.runAllTimers()", async () => {
        return testWithJestDependency(
            `jest.runAllTimers();`,
            `import {vi} from 'vitest';

vi.runAllTimers();`
        );
    });

    test("transforms jest.runOnlyPendingTimers() to vi.runOnlyPendingTimers()", async () => {
        return testWithJestDependency(
            `jest.runOnlyPendingTimers();`,
            `import {vi} from 'vitest';

vi.runOnlyPendingTimers();`
        );
    });

    test("transforms jest.advanceTimersByTime() to vi.advanceTimersByTime()", async () => {
        return testWithJestDependency(
            `jest.advanceTimersByTime(1000);`,
            `import {vi} from 'vitest';

vi.advanceTimersByTime(1000);`
        );
    });

    test("transforms complete test file with describe, it, and expect", async () => {
        return testWithJestDependency(
            `describe('MyComponent', () => {
    it('should render correctly', () => {
        const mockFn = jest.fn();
        expect(mockFn).not.toHaveBeenCalled();
    });
});`,
            `import {describe, expect, it, vi} from 'vitest';

describe('MyComponent', () => {
    it('should render correctly', () => {
        const mockFn = vi.fn();
        expect(mockFn).not.toHaveBeenCalled();
    });
});`
        );
    });

    test("transforms test file with beforeEach and afterEach", async () => {
        return testWithJestDependency(
            `describe('MyModule', () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    test('should work', () => {
        expect(true).toBe(true);
    });
});`,
            `import {afterEach, beforeEach, describe, expect, test, vi} from 'vitest';

describe('MyModule', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    test('should work', () => {
        expect(true).toBe(true);
    });
});`
        );
    });

    test("transforms test file with beforeAll and afterAll", async () => {
        return testWithJestDependency(
            `describe('MyModule', () => {
    beforeAll(() => {
        jest.useFakeTimers();
    });

    afterAll(() => {
        jest.useRealTimers();
    });

    test('should work', () => {
        expect(true).toBe(true);
    });
});`,
            `import {afterAll, beforeAll, describe, expect, test, vi} from 'vitest';

describe('MyModule', () => {
    beforeAll(() => {
        vi.useFakeTimers();
    });

    afterAll(() => {
        vi.useRealTimers();
    });

    test('should work', () => {
        expect(true).toBe(true);
    });
});`
        );
    });

    test("transforms multiple jest.fn() calls in single file", async () => {
        return testWithJestDependency(
            `const mockFn1 = jest.fn();
const mockFn2 = jest.fn();
const spy = jest.spyOn(obj, 'method');`,
            `import {vi} from 'vitest';

const mockFn1 = vi.fn();
const mockFn2 = vi.fn();
const spy = vi.spyOn(obj, 'method');`
        );
    });

    test("does not transform non-Jest code", () => {
        return spec.rewriteRun(
            typescript(
                `const myFunction = () => {
    console.log('Hello');
};`
            )
        );
    });

    test("does not transform custom jest-like functions", () => {
        return spec.rewriteRun(
            typescript(
                `const customJest = {
    fn: () => {}
};
const mockFn = customJest.fn();`
            )
        );
    });

    test("transforms JavaScript files", async () => {
        return testWithJestDependency(
            `describe('MyTest', () => {
    test('works', () => {
        const mock = jest.fn();
        expect(mock).toBeDefined();
    });
});`,
            `import {describe, expect, test, vi} from 'vitest';

describe('MyTest', () => {
    test('works', () => {
        const mock = vi.fn();
        expect(mock).toBeDefined();
    });
});`,
            true // useJavaScript
        );
    });

    test("transforms complex test scenario", async () => {
        return testWithJestDependency(
            `import { myFunction } from './myModule';

jest.mock('./myModule');
jest.setTimeout(30000);

describe('Complex Test', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        jest.useFakeTimers();
    });

    afterEach(() => {
        jest.useRealTimers();
        jest.restoreAllMocks();
    });

    test('should handle async operations', async () => {
        const mockCallback = jest.fn();
        const spy = jest.spyOn(console, 'log');
        await myFunction(mockCallback);

        jest.advanceTimersByTime(1000);
        jest.runOnlyPendingTimers();

        expect(mockCallback).toHaveBeenCalled();
        expect(jest.getTimerCount()).toBe(0);
    });

    test('should check if function is mocked', () => {
        const fn = jest.fn();
        expect(jest.isMockFunction(fn)).toBe(true);
    });
});`,
            `import { myFunction } from './myModule';
import {afterEach, beforeEach, describe, expect, test, vi} from 'vitest';

vi.mock('./myModule');
vi.setConfig({ testTimeout: 30000});

describe('Complex Test', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    test('should handle async operations', async () => {
        const mockCallback = vi.fn();
        const spy = vi.spyOn(console, 'log');
        await myFunction(mockCallback);

        vi.advanceTimersByTime(1000);
        vi.runOnlyPendingTimers();

        expect(mockCallback).toHaveBeenCalled();
        expect(vi.getTimerCount()).toBe(0);
    });

    test('should check if function is mocked', () => {
        const fn = vi.fn();
        expect(vi.isMockFunction(fn)).toBe(true);
    });
});`
        );
    });
});
