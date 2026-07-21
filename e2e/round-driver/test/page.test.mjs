import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  judgePaletteProvenance,
  selectFeaturedPlots
} from '../../../site/build-round-page.mjs'

test('page cycles a two-plot proof for its third featured view', () => {
  const plots = [{ plot_id: 'p1' }, { plot_id: 'p2' }]

  assert.deepEqual(selectFeaturedPlots(plots), [plots[0], plots[1], plots[0]])
})

test('page provenance distinguishes historical fallback and current explicit palettes', () => {
  assert.match(judgePaletteProvenance('hash-fallback-v1'), /earlier fallback colors/)
  assert.match(
    judgePaletteProvenance('explicit-proof-materials-v1'),
    /same explicit material colors/
  )
})
