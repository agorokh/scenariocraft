import { readFile } from 'node:fs/promises'

export const BOT_NAMES = Object.freeze(['Blocky', 'Crafty', 'Pixel'])
export const JUDGE_PERSONAS = Object.freeze([
  'Professor Brickworth',
  'Captain Sparkle',
  'Granny Redstone'
])
export const REQUIRED_PHASES = Object.freeze([
  'PREPARING',
  'GATHERING',
  'NOTE_PICK',
  'BUILDING',
  'REVEAL',
  'RESULTS'
])
export const JUDGE_RENDER_PALETTES = Object.freeze([
  'hash-fallback-v1',
  'explicit-proof-materials-v1'
])

const SCORE_KEYS = Object.freeze(['theme_fit', 'creativity', 'effort', 'detail'])
const BUILD_WARNINGS = Object.freeze([
  '10 minutes left — keep those great ideas growing!',
  '5 minutes left — your build is taking shape!',
  '1 minute left — add your favorite finishing touches!',
  '10 seconds left — make them count!'
])
const CRUEL_LANGUAGE = /\b(?:awful|bad|boring|disgusting|dumb|embarrassing|failure|garbage|gross|hate|horrible|idiot|incompetent|lazy|loser|nobody|pathetic|pointless|shame|stupid|sucks?|talentless|terrible|trash|ugly|useless|worthless|worst)\b|\b(?:no|without) talent\b|\black(?:s|ing)? talent\b/iu
const BUILD_FEATURE = /\b(?:arch|bridge|build|chimney|color|colour|detail|design|door|doorway|flag|floor|foundation|garden|idea|lighting|outline|palette|path|pattern|proportion|roof|room|shape|silhouette|structure|support|texture|tower|trim|wall|window)s?\b/iu
const POSITIVE_EFFECT = /\b(?:anchors?|balanced|beautiful|bold|bright|charming|clear|clever|colorful|colourful|cozy|creative|creates?|delightful|detailed|draws?|excellent|fantastic|fits?|frames?|gives?|good|great|impressive|inviting|leads?|lovely|makes?|neat|recognizable|solid|stands? out|strong|sturdy|supports?|tidy|warm|welcoming|works?)\b/iu
const IMPROVEMENT_START = /^(?:add|build|consider|experiment|focus|for the next round|give|keep|make|next|place|try|tuck|use|you can|you could)\b/iu
const CONSTRUCTIVE_START = /^(?:add|build|consider|experiment|focus|for the next round|give|keep|make|next|one next step|place|to make|try|tuck|use|you can|you could|your next step|.{1,60}\btip is to)\b/iu
const UNSAFE_TEXT = /[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/u

const PHASE_PATTERNS = Object.freeze([
  ['PREPARING', /getting the arena ready/i],
  ['GATHERING', /Gather at the hub!/],
  ['NOTE_PICK', / is the secret-note picker!/],
  ['BUILDING', /^Build time!/],
  ['REVEAL', /^Time to reveal the builds!/],
  ['RESULTS', /^Speed Build results — /]
])

export function classifyPhase(message) {
  const match = PHASE_PATTERNS.find(([, pattern]) => pattern.test(message))
  return match?.[0] ?? null
}

export function parsePicker(message) {
  return /^(.{1,64}) is the secret-note picker!/.exec(message)?.[1] ?? null
}

export function parseTask(message) {
  return /^Your build idea is: (.{1,512})!$/.exec(message)?.[1] ?? null
}

export function rejectionMessage(picker) {
  return `This secret note belongs to ${picker} this round. You'll see the idea together soon!`
}

export class RoundObservation {
  #started = false
  #phases = new Map()
  #transcript = []
  #listeners = new Set()
  #unexpectedMessage = false

  start(at = new Date().toISOString()) {
    this.#started = true
    this.startedAt = at
  }

  record(recipient, message, at = new Date().toISOString()) {
    if (!this.#started || !BOT_NAMES.includes(recipient) || !message?.trim()) return
    const trimmed = message.trim()
    if (!isProofTranscriptMessage(trimmed)) {
      this.#unexpectedMessage = true
      return
    }
    const entry = Object.freeze({ at, recipient, message: trimmed })
    this.#transcript.push(entry)
    const phase = classifyPhase(entry.message)
    if (phase && !this.#phases.has(phase)) this.#phases.set(phase, at)
    this.picker ??= parsePicker(entry.message)
    this.task ??= parseTask(entry.message)
    for (const listener of this.#listeners) listener(entry)
  }

  find(predicate) {
    return this.#transcript.find(predicate)
  }

  waitFor(predicate, timeoutMs, label) {
    const existing = this.#transcript.find(predicate)
    if (existing) return Promise.resolve(existing)
    return new Promise((resolve, reject) => {
      let timer
      const listener = entry => {
        if (!predicate(entry)) return
        clearTimeout(timer)
        this.#listeners.delete(listener)
        resolve(entry)
      }
      timer = setTimeout(() => {
        this.#listeners.delete(listener)
        reject(new Error(`Timed out waiting for ${label}.`))
      }, timeoutMs)
      this.#listeners.add(listener)
    })
  }

  finish(completedAt = new Date().toISOString()) {
    this.completedAt = completedAt
    if (this.#unexpectedMessage) {
      throw new Error('Unexpected public chat appeared during the proof round.')
    }
    const missing = REQUIRED_PHASES.filter(phase => !this.#phases.has(phase))
    if (missing.length) throw new Error(`Round transcript is missing phases: ${missing.join(', ')}`)
    return {
      schema: 1,
      started_at: this.startedAt,
      completed_at: completedAt,
      duration_seconds: Math.max(
        1,
        Math.round((Date.parse(completedAt) - Date.parse(this.startedAt)) / 1000)
      ),
      picker: this.picker,
      task: this.task,
      phase_timeline: REQUIRED_PHASES.map(phase => ({ phase, at: this.#phases.get(phase) })),
      chat_transcript: [...this.#transcript]
    }
  }
}

export function countPlacedBlocks(voxels) {
  requireExactKeys(voxels, ['schema', 'plot_id', 'origin', 'size', 'palette', 'blocks'], 'voxel')
  if (voxels.schema !== 1 || !Array.isArray(voxels.palette) || voxels.palette[0] !== 'minecraft:air') {
    throw new Error('Voxel file must use frozen schema 1 with air at palette index zero.')
  }
  if (!Array.isArray(voxels.size) || voxels.size.length !== 3 || !Array.isArray(voxels.blocks)) {
    throw new Error('Voxel file has invalid size or blocks arrays.')
  }
  const expected = voxels.size.reduce((product, value) => product * value, 1)
  if (voxels.blocks.length !== expected) throw new Error('Voxel block array does not match its size.')
  if (voxels.blocks.some(value => !Number.isInteger(value) || value < 0 || value >= voxels.palette.length)) {
    throw new Error('Voxel block array contains an invalid palette index.')
  }
  return voxels.blocks.reduce((count, value) => count + (value === 0 ? 0 : 1), 0)
}

export async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'))
}

export function buildProofManifest(gameManifest, observation, results, voxelsByPlot) {
  validateGameManifest(gameManifest)
  validateObservation(observation, gameManifest)
  validateResults(results, gameManifest)
  validateResultTranscript(observation.chat_transcript, results, gameManifest)
  const date = roundDate(gameManifest.round_id)
  const plots = gameManifest.plots.map(plot => {
    const voxels = voxelsByPlot.get(plot.plot_id)
    if (!voxels || voxels.plot_id !== plot.plot_id) {
      throw new Error(`Missing matching voxel export for ${plot.plot_id}.`)
    }
    if (JSON.stringify(voxels.origin) !== JSON.stringify(plot.origin)
        || JSON.stringify(voxels.size) !== JSON.stringify(plot.size)) {
      throw new Error(`Voxel geometry does not match the game manifest for ${plot.plot_id}.`)
    }
    const blockCount = countPlacedBlocks(voxels)
    if (blockCount < 1) throw new Error(`${plot.player} placed no blocks in ${plot.plot_id}.`)
    return {
      plot_id: plot.plot_id,
      player: plot.player,
      block_count: blockCount,
      voxel_file: `${plot.plot_id}.voxels.json`,
      renders: {
        isometric: `${plot.plot_id}-iso-ne.png`,
        plan: `${plot.plot_id}-plan.png`,
        cutaway: `${plot.plot_id}-cut-z.png`
      }
    }
  })
  const manifest = {
    schema: 1,
    round_id: gameManifest.round_id,
    date,
    task: gameManifest.task,
    started_at: observation.started_at,
    completed_at: observation.completed_at,
    duration_seconds: observation.duration_seconds,
    judge_render_palette: observation.judge_render_palette,
    picker: observation.picker,
    rejected_bot: observation.rejected_bot,
    phase_timeline: observation.phase_timeline,
    chat_transcript: observation.chat_transcript,
    plots
  }
  validateProofManifest(manifest)
  return manifest
}

export function validateProofManifest(manifest) {
  requireExactKeys(manifest, [
    'schema', 'round_id', 'date', 'task', 'started_at', 'completed_at', 'duration_seconds',
    'judge_render_palette', 'picker', 'rejected_bot', 'phase_timeline', 'chat_transcript', 'plots'
  ], 'proof manifest')
  if (manifest.schema !== 1 || !/^round-[0-9]{8}-[0-9]{6}$/.test(manifest.round_id)) {
    throw new Error('Proof manifest must use schema 1 and a frozen round id.')
  }
  if (manifest.date !== roundDate(manifest.round_id)) {
    throw new Error('Proof manifest date must match its UTC round id.')
  }
  for (const field of ['started_at', 'completed_at']) requireIsoDate(manifest[field], field)
  if (!Number.isInteger(manifest.duration_seconds) || manifest.duration_seconds < 1) {
    throw new Error('Proof duration must be a positive whole number.')
  }
  const startedAt = Date.parse(manifest.started_at)
  const completedAt = Date.parse(manifest.completed_at)
  if (completedAt < startedAt) {
    throw new Error('Proof completion must not precede its start.')
  }
  const derivedDuration = Math.max(1, Math.round((completedAt - startedAt) / 1000))
  if (manifest.duration_seconds !== derivedDuration) {
    throw new Error('Proof duration must be derived from its start and completion timestamps.')
  }
  if (!JUDGE_RENDER_PALETTES.includes(manifest.judge_render_palette)) {
    throw new Error('Proof manifest must name its judge render palette.')
  }
  if (!BOT_NAMES.includes(manifest.picker) || !BOT_NAMES.includes(manifest.rejected_bot)
      || manifest.picker === manifest.rejected_bot) {
    throw new Error('Picker and rejected bot must be different invented contestants.')
  }
  if (!Array.isArray(manifest.phase_timeline)) {
    throw new Error('Proof phase timeline must be an array.')
  }
  manifest.phase_timeline.forEach(entry =>
    requireExactKeys(entry, ['phase', 'at'], 'phase entry')
  )
  const phases = manifest.phase_timeline.map(entry => entry.phase)
  if (JSON.stringify(phases) !== JSON.stringify(REQUIRED_PHASES)) {
    throw new Error('Proof phase timeline must contain every phase in order.')
  }
  let previousTimestamp = startedAt
  manifest.phase_timeline.forEach(entry => {
    requireIsoDate(entry.at, `phase ${entry.phase}`)
    const timestamp = Date.parse(entry.at)
    if (timestamp < previousTimestamp || timestamp > completedAt) {
      throw new Error('Proof phase timestamps must be monotonic and within the round interval.')
    }
    previousTimestamp = timestamp
  })
  if (!Array.isArray(manifest.chat_transcript) || manifest.chat_transcript.length < 1) {
    throw new Error('Proof transcript must not be empty.')
  }
  previousTimestamp = startedAt
  for (const entry of manifest.chat_transcript) {
    requireExactKeys(entry, ['at', 'recipient', 'message'], 'transcript entry')
    requireIsoDate(entry.at, 'transcript entry')
    const timestamp = Date.parse(entry.at)
    if (timestamp < previousTimestamp || timestamp > completedAt) {
      throw new Error('Proof transcript timestamps must be monotonic and within the round interval.')
    }
    previousTimestamp = timestamp
    if (!BOT_NAMES.includes(entry.recipient) || typeof entry.message !== 'string'
        || !entry.message.trim() || entry.message.length > 2048
        || !isProofTranscriptMessage(entry.message)) {
      throw new Error('Proof transcript contains an invalid entry.')
    }
  }
  const derived = deriveTranscriptEvidence(manifest.chat_transcript)
  if (derived.picker !== manifest.picker || derived.task !== manifest.task
      || JSON.stringify(derived.phase_timeline) !== JSON.stringify(manifest.phase_timeline)) {
    throw new Error('Proof fields do not match the derived transcript evidence.')
  }
  const expectedRejection = rejectionMessage(manifest.picker)
  if (!manifest.chat_transcript.some(entry =>
    entry.recipient === manifest.rejected_bot && entry.message === expectedRejection)) {
    throw new Error('Proof transcript does not contain the asserted chest rejection.')
  }
  if (!Array.isArray(manifest.plots) || manifest.plots.length < 2 || manifest.plots.length > 3) {
    throw new Error('Proof manifest must contain two or three plots.')
  }
  const players = new Set()
  manifest.plots.forEach(plot => {
    requireExactKeys(plot, ['plot_id', 'player', 'block_count', 'voxel_file', 'renders'], 'proof plot')
    if (!/^p[1-9][0-9]*$/.test(plot.plot_id) || !BOT_NAMES.includes(plot.player)
        || !Number.isInteger(plot.block_count) || plot.block_count < 1) {
      throw new Error('Proof manifest contains an invalid plot.')
    }
    if (!players.add(plot.player)) throw new Error('Proof plot players must be unique.')
    if (plot.voxel_file !== `${plot.plot_id}.voxels.json`
        || plot.renders?.isometric !== `${plot.plot_id}-iso-ne.png`
        || plot.renders?.plan !== `${plot.plot_id}-plan.png`
        || plot.renders?.cutaway !== `${plot.plot_id}-cut-z.png`) {
      throw new Error(`Proof artifact names do not match ${plot.plot_id}.`)
    }
    requireExactKeys(plot.renders, ['isometric', 'plan', 'cutaway'], 'proof renders')
  })
  return manifest
}

export function validateResults(results, gameManifest) {
  requireExactKeys(
    results,
    ['schema', 'round_id', 'task', 'contestants', 'winner'],
    'judge results'
  )
  if (results?.schema !== 1 || results.round_id !== gameManifest.round_id
      || results.task !== gameManifest.task || !Array.isArray(results.contestants)) {
    throw new Error('Judge results do not match the exported round.')
  }
  const expected = gameManifest.plots.map(plot => `${plot.plot_id}:${plot.player}`).sort()
  const actual = results.contestants.map(result => `${result.plot_id}:${result.player}`).sort()
  if (JSON.stringify(expected) !== JSON.stringify(actual)) {
    throw new Error('Judge results do not cover the exported contestants exactly.')
  }
  for (const contestant of results.contestants) {
    requireExactKeys(
      contestant,
      ['plot_id', 'player', 'verdicts', 'mean', 'failures'],
      'judge contestant'
    )
    if (!Array.isArray(contestant.verdicts)
        || contestant.verdicts.length !== JUDGE_PERSONAS.length) {
      throw new Error(`${contestant.player} does not have a complete judge panel.`)
    }
    const personas = contestant.verdicts.map(verdict => verdict.persona).sort()
    if (JSON.stringify(personas) !== JSON.stringify([...JUDGE_PERSONAS].sort())) {
      throw new Error(`${contestant.player} does not have the three expected personas.`)
    }
    if (!Array.isArray(contestant.failures) || contestant.failures.length !== 0) {
      throw new Error(`${contestant.player} has failures despite a complete judge panel.`)
    }
    const verdictScores = []
    for (const verdict of contestant.verdicts) {
      requireExactKeys(verdict, ['persona', 'reasoning', 'scores', 'comment'], 'judge verdict')
      if (typeof verdict.persona !== 'string' || typeof verdict.reasoning !== 'string'
          || typeof verdict.comment !== 'string' || javaIsBlank(verdict.persona)
          || javaIsBlank(verdict.reasoning) || javaIsBlank(verdict.comment)) {
        throw new Error('Judge results contain an invalid verdict.')
      }
      validateKidSafeVerdict(verdict)
      requireExactKeys(verdict.scores, SCORE_KEYS, 'judge scores')
      const scores = SCORE_KEYS.map(key => verdict.scores[key])
      if (scores.some(score => !Number.isInteger(score) || score < 1 || score > 10)) {
        throw new Error('Judge score fields must be whole numbers from 1 through 10.')
      }
      verdictScores.push(scores.reduce((sum, score) => sum + score, 0) / scores.length)
    }
    const derivedMean = verdictScores.reduce((sum, score) => sum + score, 0) / verdictScores.length
    if (!sameNumber(contestant.mean, derivedMean)) {
      throw new Error(`${contestant.player}'s mean must be derived from the verdict scores.`)
    }
  }
  const winner = results.winner
  requireExactKeys(winner, ['plot_id', 'player', 'mean'], 'judge winner')
  if (!winner || !results.contestants.some(contestant =>
    contestant.plot_id === winner.plot_id
      && contestant.player === winner.player
      && sameNumber(contestant.mean, winner.mean))) {
    throw new Error('Judge results do not contain a matching winner.')
  }
  const means = results.contestants.map(contestant => contestant.mean)
  if (means.some(mean => typeof mean !== 'number' || !Number.isFinite(mean))
      || !sameNumber(winner.mean, Math.max(...means))
      || means.filter(mean => sameNumber(mean, winner.mean)).length !== 1) {
    throw new Error('Judge winner must have the unique highest finite mean score.')
  }
}

export function isProofTranscriptMessage(message) {
  if (typeof message !== 'string' || message.length > 2048) return false
  if (message === 'Speed Build is getting the arena ready in safe little batches!'
      || message === 'Build time! Have fun making something only you could imagine.'
      || message === 'Time to reveal the builds! The walls are coming down safely.'
      || BUILD_WARNINGS.includes(message)) {
    return true
  }
  if (/^Starting Speed Build for [23] builders!$/.test(message)
      || /^Gather at the hub! Your build idea arrives in [1-9][0-9]* seconds\.$/.test(message)
      || /^(?:gathering|note pick): [1-9][0-9]* seconds left\.$/.test(message)
      || /^The walls are down! Enjoy the build tour for [1-9][0-9]* seconds\.$/.test(message)
      || /^Speed Build results — .{1,512}$/u.test(message)) {
    return true
  }
  if (parsePicker(message) && BOT_NAMES.includes(parsePicker(message))) return true
  if (parseTask(message)) return true
  if (BOT_NAMES.some(name => message === rejectionMessage(name))) return true
  if (BOT_NAMES.some(name => message === `Winner: ${name}!`)) return true
  const feedback = /^(Blocky|Crafty|Pixel) — (Professor Brickworth|Captain Sparkle|Granny Redstone): (?:10|[0-9])\.[0-9]{2} — .+$/u.exec(message)
  return Boolean(feedback) && [...message].length <= 120 && !UNSAFE_TEXT.test(message)
}

export function resultAnnouncementLines(results) {
  const lines = [limitAnnouncement(`Speed Build results — ${cleanAnnouncement(results.task)}`, 120)]
  for (const contestant of results.contestants) {
    for (const verdict of contestant.verdicts) {
      const scores = SCORE_KEYS.map(key => verdict.scores[key])
      const score = scores.reduce((sum, value) => sum + value, 0) / scores.length
      lines.push(limitAnnouncement(
        `${cleanAnnouncement(contestant.player)} — ${cleanAnnouncement(verdict.persona)}: ${score.toFixed(2)} — ${cleanAnnouncement(verdict.comment)}`,
        120
      ))
    }
  }
  lines.push(limitAnnouncement(`Winner: ${cleanAnnouncement(results.winner.player)}!`, 64))
  return lines
}

function validateResultTranscript(transcript, results, gameManifest) {
  const expected = resultAnnouncementLines(results)
  for (const plot of gameManifest.plots) {
    const observed = transcript
      .filter(entry => entry.recipient === plot.player)
      .map(entry => entry.message)
    const offset = observed.indexOf(expected[0])
    if (offset < 0
        || JSON.stringify(observed.slice(offset)) !== JSON.stringify(expected)) {
      throw new Error(`Proof transcript is missing ${plot.player}'s exact judge result announcement.`)
    }
  }
}

function deriveTranscriptEvidence(transcript) {
  const phases = new Map()
  const pickers = new Set()
  const tasks = new Set()
  for (const entry of transcript) {
    const phase = classifyPhase(entry.message)
    if (phase && !phases.has(phase)) phases.set(phase, entry.at)
    const picker = parsePicker(entry.message)
    if (picker) pickers.add(picker)
    const task = parseTask(entry.message)
    if (task) tasks.add(task)
  }
  if (pickers.size !== 1 || tasks.size !== 1
      || REQUIRED_PHASES.some(phase => !phases.has(phase))) {
    throw new Error('Proof transcript does not derive one complete round.')
  }
  return {
    picker: [...pickers][0],
    task: [...tasks][0],
    phase_timeline: REQUIRED_PHASES.map(phase => ({ phase, at: phases.get(phase) }))
  }
}

function validateGameManifest(manifest) {
  requireExactKeys(manifest, ['schema', 'round_id', 'task', 'world', 'plots'], 'game manifest')
  if (manifest.schema !== 1 || !/^round-[0-9]{8}-[0-9]{6}$/.test(manifest.round_id)
      || !Array.isArray(manifest.plots) || manifest.plots.length < 2 || manifest.plots.length > 3) {
    throw new Error('Game manifest is outside the proof-round contract.')
  }
  const expectedPlayers = BOT_NAMES.slice(0, manifest.plots.length).sort()
  const actualPlayers = manifest.plots.map(plot => plot.player).sort()
  if (JSON.stringify(expectedPlayers) !== JSON.stringify(actualPlayers)) {
    throw new Error('Game manifest must contain only the invented proof contestants.')
  }
  manifest.plots.forEach((plot, index) => {
    requireExactKeys(plot, ['plot_id', 'player', 'origin', 'size'], 'game plot')
    if (plot.plot_id !== `p${index + 1}` || !Array.isArray(plot.origin)
        || plot.origin.length !== 3 || !Array.isArray(plot.size) || plot.size.length !== 3) {
      throw new Error('Game manifest contains invalid plot geometry.')
    }
  })
}

function validateObservation(observation, gameManifest) {
  if (observation?.schema !== 1 || !BOT_NAMES.includes(observation.picker)
      || !BOT_NAMES.includes(observation.rejected_bot) || !observation.task
      || !JUDGE_RENDER_PALETTES.includes(observation.judge_render_palette)) {
    throw new Error('Driver observation is incomplete.')
  }
  if (observation.round_id !== gameManifest.round_id || observation.task !== gameManifest.task) {
    throw new Error('Driver observation does not match the exported round.')
  }
}

function cleanAnnouncement(value) {
  let cleaned = ''
  let previousWhitespace = false
  let skipLegacyFormatCode = false
  for (const character of String(value)) {
    if (skipLegacyFormatCode) {
      skipLegacyFormatCode = false
      continue
    }
    if (character === '§') {
      skipLegacyFormatCode = true
      continue
    }
    const codePoint = character.codePointAt(0)
    const whitespace = javaIsWhitespace(codePoint)
    if (whitespace) {
      if (!previousWhitespace) cleaned += ' '
    } else if (character === '{' || character === '[') {
      cleaned += '('
    } else if (character === '}' || character === ']') {
      cleaned += ')'
    } else {
      cleaned += character
    }
    previousWhitespace = whitespace
  }
  return cleaned.replace(/^[\u0000-\u0020]+|[\u0000-\u0020]+$/gu, '')
}

function validateKidSafeVerdict(verdict) {
  if (verdict.reasoning.length > 4000 || verdict.comment.length > 500
      || verdict.comment !== javaStrip(verdict.comment)
      || UNSAFE_TEXT.test(verdict.reasoning) || UNSAFE_TEXT.test(verdict.comment)
      || CRUEL_LANGUAGE.test(verdict.reasoning) || CRUEL_LANGUAGE.test(verdict.comment)) {
    throw new Error('Judge verdict text violates the kid-safe output contract.')
  }
  const sentenceEnds = [...verdict.comment.matchAll(/[.!?]+(?=[ \t\n\v\f\r]+|$)/gu)]
    .filter(match => !isAbbreviation(verdict.comment, match.index, match.index + match[0].length))
  if (sentenceEnds.length !== 2
      || sentenceEnds.at(-1).index + sentenceEnds.at(-1)[0].length !== verdict.comment.length) {
    throw new Error('Judge comments must contain exactly two complete sentences.')
  }
  const firstSentence = verdict.comment.slice(
    0,
    sentenceEnds[0].index + sentenceEnds[0][0].length
  )
  const secondSentence = javaStrip(verdict.comment.slice(
    sentenceEnds[0].index + sentenceEnds[0][0].length
  ))
  if (IMPROVEMENT_START.test(firstSentence)
      || !BUILD_FEATURE.test(firstSentence)
      || !POSITIVE_EFFECT.test(firstSentence)
      || !CONSTRUCTIVE_START.test(secondSentence)) {
    throw new Error('Judge comments must name a genuine strength before constructive guidance.')
  }
}

function isAbbreviation(text, punctuationStart, punctuationEnd) {
  if (punctuationEnd >= javaStrip(text).length
      || punctuationEnd - punctuationStart !== 1
      || text[punctuationStart] !== '.') {
    return false
  }
  let tokenStart = punctuationStart
  while (tokenStart > 0 && !javaIsWhitespace(text.charCodeAt(tokenStart - 1))) tokenStart -= 1
  const token = text.slice(tokenStart, punctuationEnd)
  return new Set(['dr.', 'e.g.', 'etc.', 'i.e.', 'mr.', 'mrs.', 'ms.', 'prof.', 'vs.'])
    .has(token.toLowerCase()) || /^(?:[A-Za-z]\.){2,}$/.test(token)
}

function javaIsBlank(value) {
  const characters = [...String(value)]
  return characters.length === 0
    || characters.every(character => javaIsWhitespace(character.codePointAt(0)))
}

function javaStrip(value) {
  const characters = [...String(value)]
  while (characters.length && javaIsWhitespace(characters[0].codePointAt(0))) characters.shift()
  while (characters.length && javaIsWhitespace(characters.at(-1).codePointAt(0))) characters.pop()
  return characters.join('')
}

function javaIsWhitespace(codePoint) {
  return (codePoint >= 0x0009 && codePoint <= 0x000d)
    || (codePoint >= 0x001c && codePoint <= 0x0020)
    || codePoint === 0x1680
    || (codePoint >= 0x2000 && codePoint <= 0x2006)
    || (codePoint >= 0x2008 && codePoint <= 0x200a)
    || codePoint === 0x2028
    || codePoint === 0x2029
    || codePoint === 0x205f
    || codePoint === 0x3000
}

function limitAnnouncement(value, maximumCodePoints) {
  const codePoints = [...value]
  if (codePoints.length <= maximumCodePoints) return value
  return `${javaStripTrailing(codePoints.slice(0, maximumCodePoints - 1).join(''))}…`
}

function javaStripTrailing(value) {
  const characters = [...String(value)]
  while (characters.length && javaIsWhitespace(characters.at(-1).codePointAt(0))) characters.pop()
  return characters.join('')
}

function sameNumber(actual, expected) {
  return typeof actual === 'number' && Number.isFinite(actual)
    && Math.abs(actual - expected) <= Number.EPSILON * Math.max(1, Math.abs(expected)) * 8
}

function roundDate(roundId) {
  const match = /^round-([0-9]{4})([0-9]{2})([0-9]{2})-[0-9]{6}$/.exec(roundId)
  if (!match) throw new Error('Round id is invalid.')
  return `${match[1]}-${match[2]}-${match[3]}`
}

function requireIsoDate(value, field) {
  if (typeof value !== 'string' || Number.isNaN(Date.parse(value)) || !value.endsWith('Z')) {
    throw new Error(`${field} must be a UTC ISO timestamp.`)
  }
}

function requireExactKeys(value, expected, label) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`${label} must be an object.`)
  }
  const actual = Object.keys(value).sort()
  const wanted = [...expected].sort()
  if (JSON.stringify(actual) !== JSON.stringify(wanted)) {
    throw new Error(`${label} has missing or unexpected fields.`)
  }
}
