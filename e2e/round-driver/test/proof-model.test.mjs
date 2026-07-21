import assert from 'node:assert/strict'
import test from 'node:test'
import {
  BOT_NAMES,
  JUDGE_PERSONAS,
  RoundObservation,
  buildProofManifest,
  classifyPhase,
  countPlacedBlocks,
  rejectionMessage,
  resultAnnouncementLines,
  validateProofManifest,
  validateResults
} from '../lib/proof-model.mjs'

test('classifies the six transcript milestones', () => {
  const messages = [
    'Speed Build is getting the arena ready in safe little batches!',
    'Gather at the hub! Your build idea arrives in 5 seconds.',
    'Crafty is the secret-note picker! The chest is waiting at the hub.',
    'Build time! Have fun making something only you could imagine.',
    'Time to reveal the builds! The walls are coming down safely.',
    'Speed Build results — A moon base for cats'
  ]
  assert.deepEqual(messages.map(classifyPhase), [
    'PREPARING', 'GATHERING', 'NOTE_PICK', 'BUILDING', 'REVEAL', 'RESULTS'
  ])
})

test('records the complete per-recipient transcript and chest rejection', () => {
  const observation = new RoundObservation()
  observation.start('2026-07-21T12:00:00Z')
  const messages = [
    'Speed Build is getting the arena ready in safe little batches!',
    'Gather at the hub! Your build idea arrives in 5 seconds.',
    'Crafty is the secret-note picker! The chest is waiting at the hub.',
    rejectionMessage('Crafty'),
    'Your build idea is: A moon base for cats!',
    'Build time! Have fun making something only you could imagine.',
    'Time to reveal the builds! The walls are coming down safely.',
    'Speed Build results — A moon base for cats'
  ]
  messages.forEach((message, index) => observation.record(
    index === 3 ? 'Blocky' : 'Pixel',
    message,
    `2026-07-21T12:00:${String(index + 1).padStart(2, '0')}Z`
  ))
  const result = observation.finish('2026-07-21T12:01:00Z')
  assert.equal(result.picker, 'Crafty')
  assert.equal(result.task, 'A moon base for cats')
  assert.equal(result.chat_transcript.length, messages.length)
})

test('fails the round instead of publishing unexpected public chat', () => {
  const observation = new RoundObservation()
  observation.start('2026-07-21T12:00:00Z')
  observation.record('Pixel', '<UnexpectedVisitor> private identity disclosed', '2026-07-21T12:00:01Z')
  assert.throws(
    () => observation.finish('2026-07-21T12:00:02Z'),
    /Unexpected public chat/
  )
})

test('counts only non-air voxel entries', () => {
  assert.equal(countPlacedBlocks(voxels('p1', [0, 1, 0, 1])), 2)
})

test('builds and validates a proof manifest with positive per-plot counts', () => {
  const game = gameManifest()
  const observation = driverObservation()
  const results = judgeResults()
  const voxelMap = new Map(game.plots.map((plot, index) => [
    plot.plot_id,
    voxels(plot.plot_id, [0, index + 1, 0, index + 1])
  ]))
  const proof = buildProofManifest(game, observation, results, voxelMap)
  assert.equal(validateProofManifest(proof), proof)
  assert.deepEqual(proof.plots.map(plot => plot.block_count), [2, 2, 2])
})

test('rejects proof data without the non-picker chest denial', () => {
  const proof = buildProofManifest(
    gameManifest(),
    driverObservation(),
    judgeResults(),
    new Map(BOT_NAMES.map((_, index) => [
      `p${index + 1}`,
      voxels(`p${index + 1}`, [0, 1, 0, 1])
    ]))
  )
  proof.chat_transcript = proof.chat_transcript.filter(entry =>
    entry.message !== rejectionMessage(proof.picker)
  )
  assert.throws(() => validateProofManifest(proof), /chest rejection/)
})

test('rejects proof fields that contradict the transcript', () => {
  const proof = buildProofManifest(
    gameManifest(),
    driverObservation(),
    judgeResults(),
    new Map(BOT_NAMES.map((_, index) => [
      `p${index + 1}`,
      voxels(`p${index + 1}`, [0, 1, 0, 1])
    ]))
  )
  proof.phase_timeline[3].at = '2026-07-21T12:00:03.500Z'
  assert.throws(() => validateProofManifest(proof), /derived transcript evidence/)
})

test('rejects phase fields that the public schema forbids', () => {
  const proof = buildProofManifest(
    gameManifest(),
    driverObservation(),
    judgeResults(),
    new Map(BOT_NAMES.map((_, index) => [
      `p${index + 1}`,
      voxels(`p${index + 1}`, [0, 1, 0, 1])
    ]))
  )
  proof.phase_timeline[0].source = 'self-asserted'
  assert.throws(() => validateProofManifest(proof), /phase entry has missing or unexpected fields/)
})

test('rejects timing claims that are not derived from a monotonic round interval', () => {
  const proof = buildProofManifest(
    gameManifest(),
    driverObservation(),
    judgeResults(),
    new Map(BOT_NAMES.map((_, index) => [
      `p${index + 1}`,
      voxels(`p${index + 1}`, [0, 1, 0, 1])
    ]))
  )
  proof.duration_seconds = 1
  assert.throws(() => validateProofManifest(proof), /duration must be derived/)

  proof.duration_seconds = 60
  proof.phase_timeline[4].at = '2026-07-21T11:59:59Z'
  proof.chat_transcript.find(entry => classifyPhase(entry.message) === 'REVEAL').at =
    '2026-07-21T11:59:59Z'
  assert.throws(() => validateProofManifest(proof), /phase timestamps must be monotonic/)
})

test('rejects results without all three persona verdicts', () => {
  const game = gameManifest()
  const results = judgeResults()
  results.contestants[0].verdicts.pop()
  assert.throws(
    () => buildProofManifest(
      game,
      driverObservation(),
      results,
      new Map(BOT_NAMES.map((_, index) => [
        `p${index + 1}`,
        voxels(`p${index + 1}`, [0, 1, 0, 1])
      ]))
    ),
    /complete judge panel/
  )
})

test('rejects a transcript that did not deliver the complete judge result in game', () => {
  const observation = driverObservation()
  observation.chat_transcript = observation.chat_transcript.filter(entry =>
    !entry.message.startsWith('Winner: ')
  )
  assert.throws(
    () => buildProofManifest(
      gameManifest(),
      observation,
      judgeResults(),
      new Map(BOT_NAMES.map((_, index) => [
        `p${index + 1}`,
        voxels(`p${index + 1}`, [0, 1, 0, 1])
      ]))
    ),
    /judge result announcement/
  )
})

test('rejects missing scores and a declared mean unrelated to verdicts', () => {
  const missingScores = judgeResults()
  delete missingScores.contestants[0].verdicts[0].scores
  assert.throws(
    () => validateResults(missingScores, gameManifest()),
    /judge verdict has missing or unexpected fields/
  )

  const forgedMean = judgeResults()
  forgedMean.contestants[0].mean = 9
  forgedMean.winner.mean = 9
  assert.throws(
    () => validateResults(forgedMean, gameManifest()),
    /mean must be derived from the verdict scores/
  )
})

test('rejects a winner below the highest mean score', () => {
  const results = judgeResults()
  for (const verdict of results.contestants[1].verdicts) {
    Object.keys(verdict.scores).forEach(key => { verdict.scores[key] = 9 })
  }
  results.contestants[1].mean = 9
  assert.throws(
    () => validateResults(results, gameManifest()),
    /winner must have the unique highest finite mean score/
  )
})

test('rejects a declared winner when the top means are tied', () => {
  const results = judgeResults()
  for (const verdict of results.contestants[1].verdicts) {
    Object.keys(verdict.scores).forEach(key => { verdict.scores[key] = 8 })
  }
  results.contestants[1].mean = 8
  assert.throws(
    () => validateResults(results, gameManifest()),
    /winner must have the unique highest finite mean score/
  )
})

test('rejects extra transcript fields that the public schema forbids', () => {
  const proof = buildProofManifest(
    gameManifest(),
    driverObservation(),
    judgeResults(),
    new Map(BOT_NAMES.map((_, index) => [
      `p${index + 1}`,
      voxels(`p${index + 1}`, [0, 1, 0, 1])
    ]))
  )
  proof.chat_transcript[0].private_identity = 'redacted'
  assert.throws(() => validateProofManifest(proof), /transcript entry has missing or unexpected fields/)
})

test('rejects unsafe judge text even when scores and transcript shape are valid', () => {
  const cruelComment = judgeResults()
  cruelComment.contestants[0].verdicts[0].comment =
    'The bright shape works. You could add a door, loser.'
  assert.throws(
    () => validateResults(cruelComment, gameManifest()),
    /kid-safe output contract/
  )

  const controlText = judgeResults()
  controlText.contestants[0].verdicts[0].reasoning += '\u200Bhidden'
  assert.throws(
    () => validateResults(controlText, gameManifest()),
    /kid-safe output contract/
  )
})

test('matches Java announcement whitespace semantics', () => {
  const results = judgeResults()
  results.contestants[0].verdicts[0].comment =
    'The bright\u00a0shape works. Add a door next.'
  validateResults(results, gameManifest())
  assert.ok(resultAnnouncementLines(results).some(line => line.includes('bright\u00a0shape')))

  const prefix = 'Speed Build results — '
  results.task = `${'x'.repeat(118 - [...prefix].length)}\u00a0tail`
  assert.ok(resultAnnouncementLines(results)[0].endsWith('\u00a0…'))
})

function gameManifest() {
  return {
    schema: 1,
    round_id: 'round-20260721-120000',
    task: 'A moon base for cats',
    world: 'battle_world',
    plots: BOT_NAMES.map((player, index) => ({
      plot_id: `p${index + 1}`,
      player,
      origin: [index * 40, 64, 0],
      size: [4, 1, 1]
    }))
  }
}

function driverObservation() {
  const times = ['01', '02', '03', '04', '05', '06']
  const phaseMessages = [
    'Speed Build is getting the arena ready in safe little batches!',
    'Gather at the hub! Your build idea arrives in 5 seconds.',
    'Crafty is the secret-note picker! The chest is waiting at the hub.',
    'Build time! Have fun making something only you could imagine.',
    'Time to reveal the builds! The walls are coming down safely.'
  ]
  const resultLines = resultAnnouncementLines(judgeResults())
  return {
    schema: 1,
    round_id: 'round-20260721-120000',
    started_at: '2026-07-21T12:00:00Z',
    completed_at: '2026-07-21T12:01:00Z',
    duration_seconds: 60,
    judge_render_palette: 'explicit-proof-materials-v1',
    picker: 'Crafty',
    rejected_bot: 'Blocky',
    task: 'A moon base for cats',
    phase_timeline: [
      'PREPARING', 'GATHERING', 'NOTE_PICK', 'BUILDING', 'REVEAL', 'RESULTS'
    ].map((phase, index) => ({ phase, at: `2026-07-21T12:00:${times[index]}Z` })),
    chat_transcript: [
      ...phaseMessages.slice(0, 3).map((message, index) => ({
        at: `2026-07-21T12:00:${times[index]}Z`,
        recipient: index === 2 ? 'Crafty' : 'Pixel',
        message
      })),
      {
        at: '2026-07-21T12:00:03.100Z',
        recipient: 'Blocky',
        message: rejectionMessage('Crafty')
      },
      {
        at: '2026-07-21T12:00:03.200Z',
        recipient: 'Pixel',
        message: 'Your build idea is: A moon base for cats!'
      },
      ...phaseMessages.slice(3).map((message, index) => ({
        at: `2026-07-21T12:00:${times[index + 3]}Z`,
        recipient: 'Pixel',
        message
      })),
      ...BOT_NAMES.flatMap(recipient => resultLines.map(message => ({
        at: '2026-07-21T12:00:06Z',
        recipient,
        message
      })))
    ]
  }
}

function judgeResults() {
  return {
    schema: 1,
    round_id: 'round-20260721-120000',
    task: 'A moon base for cats',
    contestants: BOT_NAMES.map((player, index) => {
      const score = 8 - index
      return {
        plot_id: `p${index + 1}`,
        player,
        verdicts: JUDGE_PERSONAS.map(persona => ({
          persona,
          reasoning: 'The bright shape is clear and gives the build a strong start.',
          scores: { theme_fit: score, creativity: score, effort: score, detail: score },
          comment: 'The bright shape works. Add a door next.'
        })),
        mean: score,
        failures: []
      }
    }),
    winner: { plot_id: 'p1', player: 'Blocky', mean: 8 }
  }
}

function voxels(plotId, blocks) {
  const plotNumber = Number.parseInt(plotId.slice(1), 10)
  return {
    schema: 1,
    plot_id: plotId,
    origin: [(plotNumber - 1) * 40, 64, 0],
    size: [blocks.length, 1, 1],
    palette: ['minecraft:air', 'minecraft:lime_concrete', 'minecraft:gold_block', 'minecraft:magenta_concrete'],
    blocks
  }
}
