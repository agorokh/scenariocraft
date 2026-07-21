import { spawn } from 'node:child_process'
import {
  copyFile,
  cp,
  mkdtemp,
  mkdir,
  readdir,
  readFile,
  rm,
  stat,
  writeFile
} from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { tmpdir } from 'node:os'
import { fileURLToPath } from 'node:url'
import {
  buildProofManifest,
  readJson,
  validateProofManifest,
  validateResults
} from './lib/proof-model.mjs'

const command = process.argv[2]
const options = parseOptions(process.argv.slice(3))

try {
  if (command === 'publish') {
    await publish(options.proof || '/proof', options.site || '/site')
  } else if (command === 'check') {
    await checkSite(options.site || 'site')
  } else {
    throw new Error('Usage: assemble.mjs publish --proof <directory> --site <directory> | check --site <directory>')
  }
} catch (error) {
  console.error(`SCENARIOCRAFT_PROOF_BUNDLE_FAILURE ${safeMessage(error)}`)
  process.exitCode = 1
}

async function publish(proofDirectory, siteDirectory) {
  const observation = await readJson(join(proofDirectory, 'driver.json'))
  const roundId = observation.round_id
  requireRoundId(roundId)
  const rawRound = join(proofDirectory, 'raw', roundId)
  const gameManifest = await readJson(join(rawRound, 'manifest.json'))
  const results = await readJson(join(rawRound, 'results.json'))
  const voxelsByPlot = new Map()
  for (const plot of gameManifest.plots) {
    voxelsByPlot.set(plot.plot_id, await readJson(join(rawRound, `${plot.plot_id}.voxels.json`)))
  }
  const manifest = buildProofManifest(gameManifest, observation, results, voxelsByPlot)
  const stageRoot = join(proofDirectory, 'staged', roundId)
  const stageData = join(stageRoot, 'data')
  const stageAssets = join(stageRoot, 'assets')
  await rm(stageRoot, { recursive: true, force: true })
  await mkdir(stageData, { recursive: true })
  await mkdir(stageAssets, { recursive: true })
  await writeFile(join(stageData, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`)
  await copyFile(join(rawRound, 'manifest.json'), join(stageData, 'game-manifest.json'))
  await copyFile(join(rawRound, 'results.json'), join(stageData, 'results.json'))

  const renderer = process.env.SCENARIOCRAFT_RENDERER || '/opt/scenariocraft/renderer/bin/renderer'
  for (const plot of manifest.plots) {
    const voxelSource = join(rawRound, plot.voxel_file)
    await copyFile(voxelSource, join(stageData, plot.voxel_file))
    const renderDirectory = join(proofDirectory, 'rendered', roundId, plot.plot_id)
    await rm(renderDirectory, { recursive: true, force: true })
    await run(renderer, ['--in', voxelSource, '--out', renderDirectory])
    await copyFile(join(renderDirectory, 'iso-ne.png'), join(stageAssets, plot.renders.isometric))
    await copyFile(join(renderDirectory, 'plan.png'), join(stageAssets, plot.renders.plan))
    await copyFile(join(renderDirectory, 'cut-z.png'), join(stageAssets, plot.renders.cutaway))
  }

  const dataTarget = join(siteDirectory, 'data', 'rounds', roundId)
  const assetTarget = join(siteDirectory, 'assets', 'rounds', roundId)
  if (await exists(dataTarget) || await exists(assetTarget)) {
    throw new Error(`Site already contains ${roundId}; refusing to overwrite committed proof.`)
  }
  await mkdir(dirname(dataTarget), { recursive: true })
  await mkdir(dirname(assetTarget), { recursive: true })
  const schemaSource = fileURLToPath(new URL('./proof-manifest.schema.json', import.meta.url))
  try {
    await cp(stageData, dataTarget, { recursive: true, errorOnExist: true })
    await cp(stageAssets, assetTarget, { recursive: true, errorOnExist: true })
    await checkRound(siteDirectory, roundId, renderer)
    await mkdir(join(siteDirectory, 'data'), { recursive: true })
    await copyFile(schemaSource, join(siteDirectory, 'data', 'proof-manifest.schema.json'))
  } catch (error) {
    await Promise.all([
      rm(dataTarget, { recursive: true, force: true }),
      rm(assetTarget, { recursive: true, force: true })
    ])
    throw error
  }
  console.log(`SCENARIOCRAFT_PROOF_BUNDLE_PUBLISHED round_id=${roundId}`)
}

async function checkSite(siteDirectory) {
  const renderer = process.env.SCENARIOCRAFT_RENDERER || '/opt/scenariocraft/renderer/bin/renderer'
  const roundsRoot = join(siteDirectory, 'data', 'rounds')
  const roundIds = (await readdir(roundsRoot, { withFileTypes: true }))
    .filter(entry => entry.isDirectory() && /^round-[0-9]{8}-[0-9]{6}$/.test(entry.name))
    .map(entry => entry.name)
    .sort()
  if (roundIds.length < 1) throw new Error('The site has no committed proof round.')
  for (const roundId of roundIds) await checkRound(siteDirectory, roundId, renderer)
  const sourceSchema = await readFile(new URL('./proof-manifest.schema.json', import.meta.url))
  const publishedSchema = await readFile(join(siteDirectory, 'data', 'proof-manifest.schema.json'))
  if (!sourceSchema.equals(publishedSchema)) throw new Error('Published proof schema is stale.')
  console.log(`SCENARIOCRAFT_PROOF_BUNDLE_VALID rounds=${roundIds.length}`)
}

async function checkRound(siteDirectory, roundId, renderer) {
  requireRoundId(roundId)
  const dataDirectory = join(siteDirectory, 'data', 'rounds', roundId)
  const assetDirectory = join(siteDirectory, 'assets', 'rounds', roundId)
  const manifest = validateProofManifest(await readJson(join(dataDirectory, 'manifest.json')))
  if (manifest.round_id !== roundId) throw new Error(`Bundle directory does not match ${roundId}.`)
  const gameManifest = await readJson(join(dataDirectory, 'game-manifest.json'))
  const results = await readJson(join(dataDirectory, 'results.json'))
  validateResults(results, gameManifest)
  const voxelsByPlot = new Map()
  for (const plot of manifest.plots) {
    const voxelPath = join(dataDirectory, plot.voxel_file)
    voxelsByPlot.set(plot.plot_id, await readJson(voxelPath))
    await verifyRenders(renderer, voxelPath, assetDirectory, plot)
  }
  const rebuilt = buildProofManifest(gameManifest, {
    schema: 1,
    round_id: manifest.round_id,
    started_at: manifest.started_at,
    completed_at: manifest.completed_at,
    duration_seconds: manifest.duration_seconds,
    judge_render_palette: manifest.judge_render_palette,
    picker: manifest.picker,
    rejected_bot: manifest.rejected_bot,
    task: manifest.task,
    phase_timeline: manifest.phase_timeline,
    chat_transcript: manifest.chat_transcript
  }, results, voxelsByPlot)
  if (JSON.stringify(rebuilt) !== JSON.stringify(manifest)) {
    throw new Error(`Proof manifest does not regenerate from ${roundId}'s frozen artifacts.`)
  }
}

async function verifyRenders(renderer, voxelPath, assetDirectory, plot) {
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'scenariocraft-proof-render-'))
  try {
    await run(renderer, ['--in', voxelPath, '--out', temporaryRoot])
    const expectedFiles = {
      isometric: 'iso-ne.png',
      plan: 'plan.png',
      cutaway: 'cut-z.png'
    }
    for (const [view, generatedName] of Object.entries(expectedFiles)) {
      const committedPath = join(assetDirectory, plot.renders[view])
      await requirePng(committedPath)
      const committed = await readFile(committedPath)
      const regenerated = await readFile(join(temporaryRoot, generatedName))
      if (!committed.equals(regenerated)) {
        throw new Error(`Render does not match ${plot.voxel_file}: ${plot.renders[view]}`)
      }
    }
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true })
  }
}

async function requirePng(path) {
  const info = await stat(path)
  if (!info.isFile() || info.size < 24) throw new Error(`Render is missing or empty: ${path}`)
  const signature = (await readFile(path)).subarray(0, 8).toString('hex')
  if (signature !== '89504e470d0a1a0a') throw new Error(`Render is not a PNG: ${path}`)
}

function run(executable, arguments_) {
  return new Promise((resolve, reject) => {
    const child = spawn(executable, arguments_, { stdio: 'inherit' })
    child.on('error', reject)
    child.on('exit', code => {
      if (code === 0) resolve()
      else reject(new Error(`${executable} exited with status ${code}.`))
    })
  })
}

async function exists(path) {
  try {
    await stat(path)
    return true
  } catch (error) {
    if (error.code === 'ENOENT') return false
    throw error
  }
}

function parseOptions(arguments_) {
  const parsed = {}
  for (let index = 0; index < arguments_.length; index += 2) {
    const name = arguments_[index]
    const value = arguments_[index + 1]
    if (!/^--(?:proof|site)$/.test(name) || !value) throw new Error(`Invalid option: ${name}`)
    parsed[name.slice(2)] = value
  }
  return parsed
}

function requireRoundId(roundId) {
  if (!/^round-[0-9]{8}-[0-9]{6}$/.test(roundId)) throw new Error('Unsafe round id.')
}

function safeMessage(error) {
  const message = error instanceof Error ? error.message : String(error)
  return message.replace(/[\r\n\u0000-\u001f\u007f]/g, ' ').slice(0, 500)
}
