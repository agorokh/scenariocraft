import { readdir, readFile, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { pathToFileURL } from 'node:url'

export async function generateRoundPage(siteDirectory) {
  const indexPath = join(siteDirectory, 'index.html')
  let html = await readFile(indexPath, 'utf8')
  const roundId = await latestRoundId(siteDirectory)
  const dataDirectory = join(siteDirectory, 'data', 'rounds', roundId)
  const manifest = JSON.parse(await readFile(join(dataDirectory, 'manifest.json'), 'utf8'))
  const results = JSON.parse(await readFile(join(dataDirectory, 'results.json'), 'utf8'))
  const paletteProvenance = judgePaletteProvenance(manifest.judge_render_palette)
  const winner = results.winner
    ? results.contestants.find(contestant => contestant.plot_id === results.winner.plot_id)
    : results.contestants[0]
  if (!winner || !Array.isArray(winner.verdicts) || winner.verdicts.length !== 3) {
    throw new Error('Current proof round must contain three verdicts for the featured build.')
  }
  const assetRoot = `assets/rounds/${escapeAttribute(roundId)}`
  const pickerLine = manifest.chat_transcript.find(entry =>
    entry.message.includes(' is the secret-note picker!'))?.message
  const rejectionLine = manifest.chat_transcript.find(entry =>
    entry.recipient === manifest.rejected_bot && entry.message.includes('This secret note belongs to'))?.message
  if (!pickerLine || !rejectionLine) throw new Error('Current proof transcript is missing chest evidence.')
  const [first, second, third] = selectFeaturedPlots(manifest.plots)
  const buildingAt = manifest.phase_timeline.find(entry => entry.phase === 'BUILDING')?.at
  const revealAt = manifest.phase_timeline.find(entry => entry.phase === 'REVEAL')?.at
  const buildSeconds = Math.round((Date.parse(revealAt) - Date.parse(buildingAt)) / 1000)
  if (!Number.isInteger(buildSeconds) || buildSeconds < 1) {
    throw new Error('Current proof round has no positive observed build window.')
  }

  html = replaceRegion(html, 'hero', [
    `      <span><b>7</b> steps</span>`,
    `      <span><b>${escapeHtml(String(manifest.plots.length))}</b> robot builders</span>`,
    `      <span><b>${escapeHtml(String(manifest.duration_seconds))}s</b> played-for-real proof</span>`
  ].join('\n'))
  html = replaceRegion(html, 'picker', [
    `            <p>The real transcript chose <strong>${escapeHtml(manifest.picker)}</strong>.`,
    `              ${escapeHtml(manifest.rejected_bot)} tried first and the gate answered`,
    `              <q>${escapeHtml(rejectionLine)}</q></p>`,
    `            <p class="evidence-line"><q>${escapeHtml(pickerLine)}</q></p>`
  ].join('\n'))
  html = replaceRegion(html, 'picker-art', [
    `              <title id="picker-title">${escapeHtml(manifest.picker)} is selected to open the chest</title>`,
    `              <desc id="picker-desc">An invented block character is spotlighted by yellow pixel rays.</desc>`
  ].join('\n'))
  html = replaceRegion(html, 'picker-label',
    `              <text x="320" y="365" text-anchor="middle">${escapeHtml(manifest.picker.toUpperCase())}, YOU'RE UP!</text>`)
  html = replaceRegion(html, 'timer',
    `            <div class="bossbar" aria-label="Observed build window: ${escapeAttribute(formatClock(buildSeconds))}"><span style="width:100%"></span><b>BUILD! ${escapeHtml(formatClock(buildSeconds))}</b></div>`)
  html = replaceRegion(html, 'verdicts', verdictFigure(winner, results))
  html = replaceRegion(html, 'provenance', [
    `      <div>`,
    `        <p class="kicker">Made here, block by block</p>`,
    `        <h2 id="made-here-heading">Showcase story, played-round proof</h2>`,
    `        <p>The rainbow volcano above is a scripted showcase build, rendered by the repository's own voxel renderer from checked-in voxel data. It teaches the game; it is not presented as a player's round.</p>`,
    `      </div>`,
    `      <div class="proof-round">`,
    `        <h3>Played-for-real proof round</h3>`,
    `        <p>The separate robot round ran for ${escapeHtml(String(manifest.duration_seconds))} seconds with the verbatim task <strong>${escapeHtml(manifest.task)}</strong>. Its sparse builds stay here because proof should remain visible even when it is not the hero art.</p>`,
    `        <div class="proof-thumbnails">`,
    proofThumbnail(assetRoot, first, first.renders.isometric, 'isometric northeast'),
    proofThumbnail(assetRoot, second, second.renders.cutaway, 'cut-Z'),
    proofThumbnail(assetRoot, third, third.renders.plan, 'top-down plan'),
    `        </div>`,
    `      <ul>`,
    `        <li><b>Round</b><span><code>${escapeHtml(manifest.round_id)}</code> · ${escapeHtml(manifest.date)} UTC</span></li>`,
    `        <li><b>Contestants</b><span>the contestants were open-source robot players (Mineflayer)</span></li>`,
    `        <li><b>Round images</b><span>rendered from the blocks the robots actually placed; displayed colors use explicit entries for their exported materials</span></li>`,
    `        <li><b>Method</b><span>original renderer output; no game-client captures</span></li>`,
    `        <li><b>Verdicts</b><span>verdicts are unedited AI output; ${escapeHtml(paletteProvenance)}</span></li>`,
    `        <li><b>Source</b><span><code>site/data/rounds/${escapeHtml(roundId)}/</code></span></li>`,
    `      </ul>`,
    `      </div>`
  ].join('\n'))
  return html
}

async function main() {
  const mode = process.argv[2]
  const siteDirectory = process.argv[3] || 'site'
  if (!['--check', '--write'].includes(mode)) {
    throw new Error('Usage: build-round-page.mjs --check|--write [site-directory]')
  }
  const generated = await generateRoundPage(siteDirectory)
  const indexPath = join(siteDirectory, 'index.html')
  if (mode === '--write') {
    await writeFile(indexPath, generated)
    console.log('SCENARIOCRAFT_PROOF_PAGE_WRITTEN')
    return
  }
  if (generated !== await readFile(indexPath, 'utf8')) {
    throw new Error('site/index.html is stale; regenerate it from the committed proof round.')
  }
  console.log('SCENARIOCRAFT_PROOF_PAGE_CURRENT')
}

function verdictFigure(contestant, results) {
  const classes = ['judge-brickworth', 'judge-sparkle', 'judge-redstone']
  const verdicts = contestant.verdicts.map((verdict, index) => [
    `              <div class="judge ${classes[index]}">`,
    `                <div class="pixel-face" aria-hidden="true"><i></i><i></i><b></b></div>`,
    `                <h3>${escapeHtml(verdict.persona).replace(' ', '<br>')}</h3>`,
    `                <p><span>Real verdict · ${escapeHtml(contestant.player)}</span> “${escapeHtml(verdict.comment)}”</p>`,
    `              </div>`
  ].join('\n')).join('\n')
  const scoreValue = key => {
    const scores = contestant.verdicts.map(verdict => verdict.scores?.[key])
    if (scores.some(score => !Number.isFinite(score))) return '—'
    return escapeHtml((scores.reduce((sum, score) => sum + score, 0) / scores.length).toFixed(1))
  }
  return [
    `          <figure class="judge-stage">`,
    `            <div class="judge-grid">`,
    verdicts,
    `            </div>`,
    `            <div class="scoreboard"><span>Theme avg <b>${scoreValue('theme_fit')}</b></span><span>Creativity avg <b>${scoreValue('creativity')}</b></span><span>Effort avg <b>${scoreValue('effort')}</b></span><span>Detail avg <b>${scoreValue('detail')}</b></span><strong>${escapeHtml(Number(contestant.mean).toFixed(1))}</strong></div>`,
    `            <figcaption>Unedited AI verdicts from <code>${escapeHtml(results.round_id)}</code>; ${escapeHtml(contestant.player)} won this played round.</figcaption>`,
    `          </figure>`
  ].join('\n')
}

function proofThumbnail(assetRoot, plot, renderName, viewLabel) {
  return [
    `          <figure>`,
    `            <img src="${assetRoot}/${escapeAttribute(renderName)}" width="1024" height="1024"`,
    `                 alt="${escapeAttribute(`${viewLabel} render of ${plot.player}'s played-round plot`)}">`,
    `            <figcaption>${escapeHtml(plot.player)} placed ${escapeHtml(String(plot.block_count))} blocks; rendered from the blocks they actually placed (${escapeHtml(viewLabel)}).</figcaption>`,
    `          </figure>`
  ].join('\n')
}

function replaceRegion(html, name, content) {
  const start = `<!-- proof:${name}:start -->`
  const end = `<!-- proof:${name}:end -->`
  const first = html.indexOf(start)
  const last = html.indexOf(end)
  if (first < 0 || last < 0 || html.indexOf(start, first + start.length) >= 0
      || html.indexOf(end, last + end.length) >= 0 || first >= last) {
    throw new Error(`Page must contain exactly one valid ${name} proof region.`)
  }
  return `${html.slice(0, first + start.length)}\n${content}\n${html.slice(last)}`
}

async function latestRoundId(siteDirectory) {
  const entries = await readdir(join(siteDirectory, 'data', 'rounds'), { withFileTypes: true })
  const roundIds = entries
    .filter(entry => entry.isDirectory() && /^round-[0-9]{8}-[0-9]{6}$/.test(entry.name))
    .map(entry => entry.name)
    .sort()
  if (!roundIds.length) throw new Error('The page has no committed proof round.')
  return roundIds.at(-1)
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character])
}

function escapeAttribute(value) {
  return escapeHtml(value)
}

export function selectFeaturedPlots(plots) {
  if (!Array.isArray(plots) || plots.length < 2 || plots.length > 3) {
    throw new Error('Proof page requires two or three plots.')
  }
  return [plots[0], plots[1], plots[2] ?? plots[0]]
}

export function judgePaletteProvenance(palette) {
  if (palette === 'hash-fallback-v1') {
    return "this round's live panel saw the same voxel geometry through the renderer's earlier fallback colors"
  }
  if (palette === 'explicit-proof-materials-v1') {
    return 'the live panel and displayed images used the same explicit material colors'
  }
  throw new Error('Current proof round has no recognized judge render palette.')
}

function formatClock(seconds) {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(error => {
    console.error(`SCENARIOCRAFT_PROOF_PAGE_FAILURE ${String(error.message).replace(/[\r\n]/g, ' ')}`)
    process.exitCode = 1
  })
}
