import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { chmodSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'

const script = fileURLToPath(new URL('../../run-proof-round.sh', import.meta.url))

test('proof round fails before Docker when the operator key is missing', () => {
  const environment = { ...process.env }
  delete environment.OPENAI_API_KEY
  delete environment.SCENARIOCRAFT_DEMO_DRY_RUN

  const result = spawnSync(script, { env: environment, encoding: 'utf8' })

  assert.equal(result.status, 2)
  assert.equal(
    result.stderr,
    'OPENAI_API_KEY is required. Export it before running make proof-round.\n'
  )
})

test('proof round refuses inherited dry-run judge mode', () => {
  const result = spawnSync(script, {
    env: {
      ...process.env,
      OPENAI_API_KEY: 'test-key-is-never-sent',
      SCENARIOCRAFT_DEMO_DRY_RUN: 'true'
    },
    encoding: 'utf8'
  })

  assert.equal(result.status, 2)
  assert.equal(
    result.stderr,
    'make proof-round requires the live judge; unset SCENARIOCRAFT_DEMO_DRY_RUN.\n'
  )
})

test('proof round uses container root for rootless Docker bind mounts', () => {
  const fakeBin = mkdtempSync(join(tmpdir(), 'scenariocraft-proof-script-'))
  const identityLog = join(fakeBin, 'identity.log')
  writeExecutable(join(fakeBin, 'uname'), '#!/bin/sh\nprintf "Linux\\n"\n')
  writeExecutable(join(fakeBin, 'id'), [
    '#!/bin/sh',
    'case "$1" in',
    '  -u) printf "123\\n" ;;',
    '  -g) printf "456\\n" ;;',
    'esac',
    ''
  ].join('\n'))
  writeExecutable(join(fakeBin, 'docker'), [
    '#!/bin/sh',
    'if [ "$1" = "compose" ] && [ "$2" = "version" ]; then exit 0; fi',
    'if [ "$1" = "info" ]; then printf \"%s\\n\" \"$FAKE_DOCKER_SECURITY_OPTIONS\"; exit 0; fi',
    'printf \"%s:%s\\n\" \"$SCENARIOCRAFT_PROOF_UID\" \"$SCENARIOCRAFT_PROOF_GID\" >\"$FAKE_IDENTITY_LOG\"',
    'exit 91',
    ''
  ].join('\n'))

  const result = spawnSync(script, {
    env: {
      ...process.env,
      PATH: `${fakeBin}:${process.env.PATH}`,
      OPENAI_API_KEY: 'test-key-is-never-sent',
      SCENARIOCRAFT_DEMO_DRY_RUN: 'false',
      FAKE_DOCKER_SECURITY_OPTIONS: '["name=rootless"]',
      FAKE_IDENTITY_LOG: identityLog
    },
    encoding: 'utf8'
  })

  assert.equal(result.status, 91)
  assert.equal(readFileSync(identityLog, 'utf8'), '0:0\n')
})

test('proof round bounds stalled Docker security detection', () => {
  const fakeBin = mkdtempSync(join(tmpdir(), 'scenariocraft-proof-timeout-'))
  writeExecutable(join(fakeBin, 'docker'), [
    '#!/bin/sh',
    'if [ "$1" = "compose" ] && [ "$2" = "version" ]; then exit 0; fi',
    'if [ "$1" = "info" ]; then while :; do sleep 10; done; fi',
    'exit 90',
    ''
  ].join('\n'))

  const result = spawnSync(script, {
    env: {
      ...process.env,
      PATH: `${fakeBin}:${process.env.PATH}`,
      OPENAI_API_KEY: 'test-key-is-never-sent',
      SCENARIOCRAFT_DEMO_DRY_RUN: 'false',
      SCENARIOCRAFT_PROOF_TIMEOUT_SECONDS: '1'
    },
    encoding: 'utf8',
    timeout: 5_000
  })

  assert.equal(result.status, 124)
  assert.match(result.stderr, /Timed out reading Docker security options\./)
})

function writeExecutable(path, contents) {
  writeFileSync(path, contents)
  chmodSync(path, 0o755)
}
