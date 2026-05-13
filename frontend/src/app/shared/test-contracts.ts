export function testContractUrl(relativePath: string): string {
  const globalState = globalThis as typeof globalThis & {
    process?: { env?: Record<string, string | undefined> };
  };

  const baseUrl = globalState.location?.origin
    ?? globalState.process?.env?.['E2E_BASE_URL']
    ?? 'http://127.0.0.1:4200';

  return new URL(`/test-contracts/${relativePath}`, baseUrl).toString();
}

export async function loadTestContractText(relativePath: string): Promise<string> {
  const globalState = globalThis as typeof globalThis & {
    process?: { env?: Record<string, string | undefined> };
  };

  const baseUrl = globalState.location?.origin
    ?? globalState.process?.env?.['E2E_BASE_URL']
    ?? 'http://127.0.0.1:4200';

  const candidates = [
    new URL(`/test-contracts/${relativePath}`, baseUrl).toString(),
    new URL(`/base/test-contracts/${relativePath}`, baseUrl).toString(),
    new URL(`/_karma_webpack_/test-contracts/${relativePath}`, baseUrl).toString()
  ];

  for (const candidate of candidates) {
    const response = await fetch(candidate);
    if (response.ok) {
      return response.text();
    }
  }

  throw new Error(`Failed to load test contract ${relativePath} from any known test asset path.`);
}

export async function loadTestContractJson<T>(relativePath: string): Promise<T> {
  return JSON.parse(await loadTestContractText(relativePath)) as T;
}
