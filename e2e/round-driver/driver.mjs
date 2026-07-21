import mineflayer from 'mineflayer'
import { createRequire } from 'node:module'
import { cp, mkdir, readdir, readFile, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { Vec3 } from 'vec3'
import {
  BOT_NAMES,
  RoundObservation,
  rejectionMessage
} from './lib/proof-model.mjs'

const require = createRequire(import.meta.url)
const host = process.env.SCENARIOCRAFT_SERVER_HOST || 'paper'
const port = positiveInteger(process.env.SCENARIOCRAFT_SERVER_PORT || '25565', 'server port')
const timeoutMs = positiveInteger(
  process.env.SCENARIOCRAFT_PROOF_TIMEOUT_SECONDS || '1200',
  'proof timeout'
) * 1000
const roundsDirectory = process.env.SCENARIOCRAFT_ROUNDS_DIR || '/rounds'
const proofDirectory = process.env.SCENARIOCRAFT_PROOF_DIR || '/proof'
const startCommand = process.env.SCENARIOCRAFT_START_COMMAND || '/speedbuild start'
const joinDelayMs = positiveInteger(
  process.env.SCENARIOCRAFT_BOT_JOIN_DELAY_MS || '5000',
  'bot join delay'
)
const botBlocks = ['lime_concrete', 'magenta_concrete', 'gold_block']
const botStructures = [
  [
    [2, 0, 0], [3, 0, 0], [2, 0, 1], [3, 0, 1],
    [2, 1, 0], [2, 2, 0]
  ],
  [
    [2, 0, 0], [3, 0, 0], [4, 0, 0],
    [2, 0, 1], [3, 0, 1], [4, 0, 1],
    [2, 0, 2], [3, 0, 2], [4, 0, 2],
    [2, 1, 0], [2, 2, 0]
  ],
  [
    [2, 0, 0], [3, 0, 0], [4, 0, 0],
    [2, 0, 1], [3, 0, 1], [4, 0, 1],
    [2, 0, 2], [3, 0, 2], [4, 0, 2],
    [2, 1, 0], [4, 1, 0], [2, 1, 2], [4, 1, 2],
    [2, 2, 0], [4, 2, 0], [2, 2, 2], [4, 2, 2],
    [3, 1, 1]
  ]
]
const observation = new RoundObservation()
const bots = []

try {
  const previousRounds = new Set(await roundDirectories())
  for (const [index, name] of BOT_NAMES.entries()) {
    bots.push(await connectBot(name))
    if (index < BOT_NAMES.length - 1) {
      await new Promise(resolve => setTimeout(resolve, joinDelayMs))
    }
  }
  observation.start()
  bots[0].chat(startCommand)

  await observation.waitFor(
    entry => entry.message.includes('Gather at the hub!'),
    timeoutMs,
    'the gathering phase'
  )
  const pickerEntry = await observation.waitFor(
    entry => entry.message.includes(' is the secret-note picker!'),
    timeoutMs,
    'the note picker'
  )
  const pickerName = /^(.+?) is the secret-note picker!/.exec(pickerEntry.message)?.[1]
  if (!BOT_NAMES.includes(pickerName)) throw new Error(`Unexpected picker: ${pickerName}`)
  const picker = botByName(pickerName)
  const rejected = bots.find(bot => bot.username !== pickerName)
  const chest = rejected.findBlock({ matching: block => block.name === 'chest', maxDistance: 12 })
  if (!chest) throw new Error(`${rejected.username} could not find the Secret Chest.`)

  const rejection = observation.waitFor(
    entry => entry.recipient === rejected.username
      && entry.message === rejectionMessage(pickerName),
    10_000,
    `${rejected.username}'s Secret Chest rejection`
  )
  let rejectedWindowOpened = false
  const onRejectedWindowOpen = () => { rejectedWindowOpened = true }
  rejected.on('windowOpen', onRejectedWindowOpen)
  try {
    await rejected.activateBlock(chest)
    await rejection
    // Give an incorrectly opened inventory time to arrive after the denial message.
    await new Promise(resolve => setTimeout(resolve, 500))
  } finally {
    rejected.off('windowOpen', onRejectedWindowOpen)
  }
  const rejectedSawTask = observation.find(entry =>
    entry.recipient === rejected.username && /^Your build idea is: .+!$/.test(entry.message)
  )
  if (rejectedWindowOpened || rejected.currentWindow || rejectedSawTask) {
    throw new Error('The non-picker received Secret Chest access despite the rejection message.')
  }

  const reveal = observation.waitFor(
    entry => /^Your build idea is: .+!$/.test(entry.message),
    10_000,
    'the build idea reveal'
  )
  const pickerChest = picker.findBlock({ matching: block => block.name === 'chest', maxDistance: 12 })
  if (!pickerChest) throw new Error(`${pickerName} could not find the Secret Chest.`)
  await picker.activateBlock(pickerChest)
  await reveal
  if (picker.currentWindow) picker.closeWindow(picker.currentWindow)

  await observation.waitFor(
    entry => entry.message.startsWith('Build time!'),
    timeoutMs,
    'the building phase'
  )
  const placedCounts = await Promise.all(
    bots.map((bot, index) => buildInPlot(bot, botBlocks[index], botStructures[index]))
  )
  console.log(
    `SCENARIOCRAFT_PROOF_PLACEMENT_SUCCESS bots=${bots.length} blocks=${placedCounts.reduce((sum, count) => sum + count, 0)}`
  )

  await observation.waitFor(
    entry => entry.message.startsWith('Time to reveal the builds!'),
    timeoutMs,
    'the reveal phase'
  )
  const roundId = await waitUntil(async () => {
    const current = await roundDirectories()
    return current.find(name => !previousRounds.has(name) && /^round-[0-9]{8}-[0-9]{6}$/.test(name))
  }, timeoutMs, 'the exported round')
  const sourceRound = join(roundsDirectory, roundId)
  await waitUntil(async () => fileExists(join(sourceRound, 'results.json')), timeoutMs, 'judge results')
  await observation.waitFor(
    entry => /^Speed Build results — /.test(entry.message),
    timeoutMs,
    'the in-game result announcement'
  )

  const results = JSON.parse(await readFile(join(sourceRound, 'results.json'), 'utf8'))
  if (!results.winner) throw new Error('The real judge completed without a winner.')
  for (const bot of bots) {
    await observation.waitFor(
      entry => entry.recipient === bot.username
        && entry.message === `Winner: ${results.winner.player}!`,
      timeoutMs,
      `${bot.username}'s complete in-game result announcement`
    )
  }
  const completed = observation.finish()
  const driverResult = {
    ...completed,
    round_id: roundId,
    rejected_bot: rejected.username,
    judge_render_palette: 'explicit-proof-materials-v1'
  }
  await mkdir(proofDirectory, { recursive: true })
  const rawDestination = join(proofDirectory, 'raw', roundId)
  await rm(rawDestination, { recursive: true, force: true })
  await mkdir(join(proofDirectory, 'raw'), { recursive: true })
  await cp(sourceRound, rawDestination, { recursive: true, errorOnExist: true })
  await writeFile(join(proofDirectory, 'driver.json'), `${JSON.stringify(driverResult, null, 2)}\n`)
  console.log(`SCENARIOCRAFT_PROOF_DRIVER_SUCCESS round_id=${roundId} elapsed_seconds=${completed.duration_seconds}`)
} catch (error) {
  console.error(`SCENARIOCRAFT_PROOF_DRIVER_FAILURE ${safeMessage(error)}`)
  process.exitCode = 1
} finally {
  for (const bot of bots) {
    try {
      bot.quit('Proof round complete.')
    } catch {
      // The server may already have closed the connection during a failed proof.
    }
  }
}

async function connectBot(username) {
  const bot = mineflayer.createBot({ host, port, username, auth: 'offline', version: false })
  bot.on('messagestr', message => observation.record(username, message))
  bot.on('kicked', reason => console.error(`${username} was kicked: ${safeMessage(reason)}`))
  bot.on('error', error => console.error(`${username} connection error: ${safeMessage(error)}`))
  await eventWithTimeout(bot, 'spawn', 90_000, `${username} to spawn`)
  return bot
}

async function buildInPlot(bot, blockName, structure) {
  await waitUntil(
    () => bot.player?.gamemode === 1 && bot.entity?.position,
    20_000,
    `${bot.username} to enter creative mode`
  )
  await withTimeout(bot.waitForChunksToLoad(), 20_000, `${bot.username}'s plot chunks`)
  const Item = require('prismarine-item')(bot.registry)
  const itemType = bot.registry.itemsByName[blockName]
  if (!itemType) throw new Error(`${blockName} is unavailable for ${bot.version}.`)
  // Use a non-default hotbar slot and wait for Paper to accept it before placing.
  // This avoids racing the initial selected-slot state when several bots join together.
  const hotbarSlot = 37
  const quickBarSlot = 1
  await bot.creative.setInventorySlot(hotbarSlot, new Item(itemType.id, 64), 2_000)
  bot.setQuickBarSlot(quickBarSlot)
  await bot.waitForTicks(2)
  const item = bot.inventory.slots[hotbarSlot]
  if (!item) throw new Error(`${bot.username} did not receive ${blockName}.`)
  if (item.name !== blockName || bot.quickBarSlot !== quickBarSlot
      || bot.heldItem?.name !== blockName) {
    throw new Error(`${bot.username} is not holding ${blockName}.`)
  }

  const center = bot.entity.position.floored()
  const targets = structure.map(([x, y, z]) => center.offset(x, y, z))
  for (const target of targets) {
    const reference = bot.blockAt(target.offset(0, -1, 0))
    if (!reference || reference.name === 'air') {
      throw new Error(`${bot.username} has no support block at ${target}.`)
    }
    await placeBlockWithRetries(bot, reference, target, blockName)
  }
  return targets.length
}

async function placeBlockWithRetries(bot, reference, target, blockName) {
  let lastFailure = new Error('the server did not acknowledge the placement')
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    await bot.lookAt(reference.position.offset(0.5, 1, 0.5), true)
    try {
      await bot.placeBlock(reference, new Vec3(0, 1, 0))
    } catch (error) {
      lastFailure = error
    }
    try {
      await waitUntil(
        () => bot.blockAt(target)?.name === blockName,
        1_500,
        `${bot.username}'s block at ${target}`
      )
      return
    } catch (error) {
      lastFailure = error
    }
    if (attempt < 3) {
      console.warn(
        `${bot.username} placement retry attempt=${attempt + 1}/3 target=${target}`
      )
      await new Promise(resolve => setTimeout(resolve, 500))
    }
  }
  const observed = bot.blockAt(target)?.name
  throw new Error(
    `${bot.username} could not place ${blockName} at ${target}; client observed ${observed || 'no block'} (${safeMessage(lastFailure)})`
  )
}

function botByName(name) {
  const bot = bots.find(candidate => candidate.username === name)
  if (!bot) throw new Error(`Bot ${name} is not connected.`)
  return bot
}

async function roundDirectories() {
  try {
    return (await readdir(roundsDirectory, { withFileTypes: true }))
      .filter(entry => entry.isDirectory())
      .map(entry => entry.name)
      .sort()
  } catch (error) {
    if (error.code === 'ENOENT') return []
    throw error
  }
}

async function fileExists(path) {
  try {
    await readFile(path)
    return true
  } catch (error) {
    if (error.code === 'ENOENT') return false
    throw error
  }
}

async function waitUntil(check, maximumMs, label) {
  const deadline = Date.now() + maximumMs
  while (Date.now() < deadline) {
    const value = await check()
    if (value) return value
    await new Promise(resolve => setTimeout(resolve, 250))
  }
  throw new Error(`Timed out waiting for ${label}.`)
}

function eventWithTimeout(emitter, event, maximumMs, label) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup()
      reject(new Error(`Timed out waiting for ${label}.`))
    }, maximumMs)
    const onEvent = value => {
      cleanup()
      resolve(value)
    }
    const onError = error => {
      cleanup()
      reject(error)
    }
    const cleanup = () => {
      clearTimeout(timer)
      emitter.off(event, onEvent)
      emitter.off('error', onError)
    }
    emitter.once(event, onEvent)
    emitter.once('error', onError)
  })
}

function withTimeout(promise, maximumMs, label) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(
      () => reject(new Error(`Timed out waiting for ${label}.`)),
      maximumMs
    )
    promise.then(
      value => {
        clearTimeout(timer)
        resolve(value)
      },
      error => {
        clearTimeout(timer)
        reject(error)
      }
    )
  })
}

function positiveInteger(value, label) {
  if (!/^[1-9][0-9]*$/.test(value)) throw new Error(`${label} must be a positive integer.`)
  return Number.parseInt(value, 10)
}

function safeMessage(error) {
  const message = error instanceof Error ? error.message : String(error)
  return message.replace(/[\r\n\u0000-\u001f\u007f]/g, ' ').slice(0, 500)
}
