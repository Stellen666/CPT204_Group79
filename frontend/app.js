const SORT_RUNS = 5;
const SORT_WARMUPS = 2;
const SORT_TARGET_SAMPLE_MS = 12;
const SORT_MAX_BATCH_ITEMS = 220000;
const DEFAULT_SORT_ALGORITHM = "merge";
const DEFAULT_BUNDLED_ROUTE = {
  source: "L0001",
  destination: "L0201",
  waypoints: ["L0105", "L0205"],
};

const state = {
  candidates: { A: [], B: [], C: [] },
  graph: null,
  positions: new Map(),
  lastSortTimings: [],
  lastPath: [],
  lastVisited: [],
  lastMstEdges: [],
  routeEditedByUser: false,
};

const els = {
  loadSamplesButton: document.getElementById("loadSamplesButton"),
  runButton: document.getElementById("runButton"),
  datasetStatus: document.getElementById("datasetStatus"),
  candidateRows: document.getElementById("candidateRows"),
  graphVertices: document.getElementById("graphVertices"),
  graphEdges: document.getElementById("graphEdges"),
  lastRuntime: document.getElementById("lastRuntime"),
  datasetSelect: document.getElementById("datasetSelect"),
  graphAlgorithmSelect: document.getElementById("graphAlgorithmSelect"),
  representationSelect: document.getElementById("representationSelect"),
  sourceSelect: document.getElementById("sourceSelect"),
  destinationSelect: document.getElementById("destinationSelect"),
  waypointInput: document.getElementById("waypointInput"),
  top10Body: document.getElementById("top10Body"),
  top10Subtitle: document.getElementById("top10Subtitle"),
  pathSummary: document.getElementById("pathSummary"),
  resultOutput: document.getElementById("resultOutput"),
  runtimeChart: document.getElementById("runtimeChart"),
  graphCanvas: document.getElementById("graphCanvas"),
};

const SORT_ALGORITHMS = {
  bubble: { label: "Bubble Sort", run: bubbleSort },
  quick: { label: "Quick Sort", run: quickSort },
  quickM3: { label: "Quick Sort Median-of-Three", run: quickSortMedianThree },
  merge: { label: "Merge Sort", run: mergeSort },
};

els.loadSamplesButton.addEventListener("click", loadBundledDatasets);
els.runButton.addEventListener("click", runAnalysis);
els.datasetSelect.addEventListener("change", runAnalysis);
els.graphAlgorithmSelect.addEventListener("change", updateControlsForGraphMode);
els.representationSelect.addEventListener("change", runAnalysis);
els.sourceSelect.addEventListener("change", () => markRouteEditedByUser(true));
els.destinationSelect.addEventListener("change", () => markRouteEditedByUser(true));
els.waypointInput.addEventListener("input", () => markRouteEditedByUser(false));

bindFileInput("candidateAInput", text => {
  state.candidates.A = parseCandidates(text);
  state.routeEditedByUser = true;
  refreshAfterDataChange();
});
bindFileInput("candidateBInput", text => {
  state.candidates.B = parseCandidates(text);
  state.routeEditedByUser = true;
  refreshAfterDataChange();
});
bindFileInput("candidateCInput", text => {
  state.candidates.C = parseCandidates(text);
  state.routeEditedByUser = true;
  refreshAfterDataChange();
});
bindFileInput("pathsInput", text => {
  state.graph = buildGraph(parsePaths(text));
  buildPositions();
  state.routeEditedByUser = true;
  refreshAfterDataChange();
});

updateControlsForGraphMode();
drawRuntimeChart([]);
drawGraph();

function bindFileInput(id, onText) {
  document.getElementById(id).addEventListener("change", async event => {
    const file = event.target.files[0];
    if (!file) return;
    onText(await file.text());
  });
}

async function loadBundledDatasets() {
  try {
    const [a, b, c, paths] = await Promise.all([
      fetchText(dataUrl("candidates_A.csv")),
      fetchText(dataUrl("candidates_B.csv")),
      fetchText(dataUrl("candidates_C.csv")),
      fetchText(dataUrl("paths.csv")),
    ]);
    state.candidates.A = parseCandidates(a);
    state.candidates.B = parseCandidates(b);
    state.candidates.C = parseCandidates(c);
    state.graph = buildGraph(parsePaths(paths));
    buildPositions();
    state.routeEditedByUser = false;
    updateDatasetStatus();
    populateVertexSelects();
    applyBundledRoutePreset();
    runAnalysis();
  } catch (error) {
    els.datasetStatus.textContent = "Could not load bundled datasets. Start a local server from the project root, then open /frontend/index.html.\n\n" + error.message;
  }
}

function dataUrl(fileName) {
  const path = window.location.pathname;
  if (path.includes("/frontend/")) {
    return `../data/${fileName}`;
  }
  return `data/${fileName}`;
}

async function fetchText(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`${url}: ${response.status}`);
  }
  return response.text();
}

function refreshAfterDataChange() {
  updateDatasetStatus();
  populateVertexSelects();
  runAnalysis();
}

function markRouteEditedByUser(clearPresetWaypoints) {
  if (!state.graph) {
    return;
  }
  state.routeEditedByUser = true;
  if (clearPresetWaypoints && els.waypointInput.dataset.preset === "true") {
    els.waypointInput.value = "";
    delete els.waypointInput.dataset.preset;
  }
  runAnalysis();
}

function updateDatasetStatus() {
  const graph = state.graph;
  els.datasetStatus.textContent = [
    `Candidates A: ${state.candidates.A.length} rows`,
    `Candidates B: ${state.candidates.B.length} rows`,
    `Candidates C: ${state.candidates.C.length} rows`,
    `Paths graph: ${graph ? `${graph.vertices.length} vertices, ${graph.edgeCount} edges` : "not loaded"}`,
  ].join("\n");
}

function populateVertexSelects() {
  const vertices = state.graph ? state.graph.vertices : [];
  const currentSource = els.sourceSelect.value;
  const currentDestination = els.destinationSelect.value;
  fillSelect(els.sourceSelect, vertices);
  fillSelect(els.destinationSelect, vertices);
  els.sourceSelect.value = vertices.includes(currentSource) ? currentSource : (vertices[0] || "");
  els.destinationSelect.value = vertices.includes(currentDestination) ? currentDestination : (vertices[9] || vertices[0] || "");
}

function applyBundledRoutePreset() {
  if (!state.graph || state.routeEditedByUser) {
    return;
  }

  if (state.graph.index.has(DEFAULT_BUNDLED_ROUTE.source)) {
    els.sourceSelect.value = DEFAULT_BUNDLED_ROUTE.source;
  }
  if (state.graph.index.has(DEFAULT_BUNDLED_ROUTE.destination)) {
    els.destinationSelect.value = DEFAULT_BUNDLED_ROUTE.destination;
  }

  const validWaypoints = DEFAULT_BUNDLED_ROUTE.waypoints.filter(id => state.graph.index.has(id));
  els.waypointInput.value = validWaypoints.join(",");
  els.waypointInput.dataset.preset = "true";
}

function fillSelect(select, values) {
  select.innerHTML = values.map(value => `<option value="${escapeHtml(value)}">${escapeHtml(value)}</option>`).join("");
}

function updateControlsForGraphMode() {
  const mode = els.graphAlgorithmSelect.value;
  const isTraversal = mode === "bfs" || mode === "dfs" || mode === "prim";
  const isTsp = mode === "tsp";
  els.destinationSelect.disabled = isTraversal || isTsp;
  els.waypointInput.placeholder = isTsp ? "L0001,L0002,L0101,L0201" : "Optional: L0105,L0205";
  runAnalysis();
}

function runAnalysis() {
  const datasetKey = els.datasetSelect.value;
  const candidates = state.candidates[datasetKey];
  if (!candidates.length) {
    clearResults("Load candidate data first.");
    return;
  }

  const sortResult = runSortingSection(candidates);
  const graphResult = state.graph ? runGraphSection(sortResult.top10) : { text: "Load paths.csv to run graph algorithms.", runtimeMs: 0 };

  els.candidateRows.textContent = candidates.length.toLocaleString();
  els.graphVertices.textContent = state.graph ? state.graph.vertices.length.toLocaleString() : "0";
  els.graphEdges.textContent = state.graph ? state.graph.edgeCount.toLocaleString() : "0";
  els.lastRuntime.textContent = formatDuration(graphResult.runtimeMs);
  els.resultOutput.textContent = graphResult.text;
}

function clearResults(message) {
  els.top10Body.innerHTML = "";
  els.top10Subtitle.textContent = message;
  els.resultOutput.textContent = message;
  els.pathSummary.textContent = message;
  els.lastRuntime.textContent = "0 ms";
  drawRuntimeChart([]);
  drawGraph();
}

function runSortingSection(candidates) {
  const selectedAlgorithm = DEFAULT_SORT_ALGORITHM;
  const timings = [];
  let selectedSorted = null;

  for (const [key, algorithm] of Object.entries(SORT_ALGORITHMS)) {
    const measured = measureSort(candidates, algorithm.run);
    timings.push({
      key,
      label: algorithm.label,
      averageMs: measured.averageMs,
      bestMs: measured.bestMs,
    });
    if (key === selectedAlgorithm) {
      selectedSorted = measured.sorted;
    }
  }

  const top10 = selectedSorted.slice(0, 10);
  renderTop10(top10, `${SORT_ALGORITHMS[selectedAlgorithm].label} fixed ranking`);
  drawRuntimeChart(timings, selectedAlgorithm);
  state.lastSortTimings = timings;
  return { top10, timings };
}

function measureSort(candidates, sorter) {
  for (let i = 0; i < SORT_WARMUPS; i++) {
    sorter(copyCandidates(candidates));
  }

  const batchSize = chooseSortBatchSize(candidates, sorter);
  let total = 0;
  let best = Infinity;
  let latestSorted = [];
  for (let i = 0; i < SORT_RUNS; i++) {
    const batch = makeCandidateBatch(candidates, batchSize);
    const start = performance.now();
    for (let j = 0; j < batch.length; j++) {
      latestSorted = sorter(batch[j]);
    }
    const elapsed = (performance.now() - start) / batchSize;
    total += elapsed;
    best = Math.min(best, elapsed);
  }

  return {
    averageMs: total / SORT_RUNS,
    bestMs: best,
    sorted: latestSorted,
  };
}

function chooseSortBatchSize(candidates, sorter) {
  const maxBatchSize = Math.max(1, Math.floor(SORT_MAX_BATCH_ITEMS / Math.max(1, candidates.length)));
  let batchSize = 1;

  while (batchSize < maxBatchSize) {
    const batch = makeCandidateBatch(candidates, batchSize);
    const start = performance.now();
    for (let i = 0; i < batch.length; i++) {
      sorter(batch[i]);
    }
    const elapsed = performance.now() - start;
    if (elapsed >= SORT_TARGET_SAMPLE_MS) {
      break;
    }
    batchSize *= 2;
  }

  return Math.min(batchSize, maxBatchSize);
}

function makeCandidateBatch(candidates, batchSize) {
  const batch = [];
  for (let i = 0; i < batchSize; i++) {
    batch.push(copyCandidates(candidates));
  }
  return batch;
}

function renderTop10(top10, algorithmName) {
  els.top10Subtitle.textContent = `${algorithmName}, priority desc + location asc`;
  els.top10Body.innerHTML = top10.map((candidate, index) => `
    <tr>
      <td>${index + 1}</td>
      <td>${escapeHtml(candidate.locationId)}</td>
      <td>${candidate.priorityScore.toLocaleString()}</td>
    </tr>
  `).join("");
}

function runGraphSection(top10) {
  const algorithm = els.graphAlgorithmSelect.value;
  const representation = els.representationSelect.value;
  const graph = representation === "matrix" ? buildMatrixGraph(state.graph) : state.graph;
  const source = els.sourceSelect.value || top10[0]?.locationId;
  const destination = els.destinationSelect.value || top10[9]?.locationId || source;
  const waypoints = parseIdList(els.waypointInput.value);

  if (!source || !state.graph.index.has(source)) {
    return { text: "Choose a valid source vertex.", runtimeMs: 0 };
  }

  if (algorithm === "bfs") {
    return runTraversal("BFS", representation, () => representation === "matrix" ? matrixBfs(graph, source) : bfs(graph, source));
  }
  if (algorithm === "dfs") {
    return runTraversal("DFS", representation, () => representation === "matrix" ? matrixDfs(graph, source) : dfs(graph, source));
  }
  if (algorithm === "prim") {
    return runPrim(representation, graph, source);
  }
  if (algorithm === "tsp") {
    const targets = waypoints.length ? waypoints : top10.slice(0, 8).map(candidate => candidate.locationId);
    return runTsp(targets);
  }
  if (algorithm === "floyd") {
    const routePoints = [source, ...waypoints, destination].filter(Boolean);
    return runFloydRoute(routePoints);
  }

  const routePoints = [source, ...waypoints, destination].filter(Boolean);
  return runDijkstraRoute(representation, graph, routePoints);
}

function runTraversal(name, representation, run) {
  const start = performance.now();
  const result = run();
  const runtimeMs = performance.now() - start;
  state.lastPath = [];
  state.lastVisited = result.order;
  state.lastMstEdges = [];
  drawGraph({ visited: result.order });
  els.pathSummary.textContent = `${name} visited ${result.order.length} vertices with ${representationLabel(representation)}.`;
  return {
    runtimeMs,
    text: [
      `${name} traversal (${representationLabel(representation)})`,
      `Start: ${result.start}`,
      `Visited vertices: ${result.order.length}`,
      `Runtime: ${formatDuration(runtimeMs)}`,
      "",
      `Order preview: ${result.order.slice(0, 35).join(" -> ")}${result.order.length > 35 ? " -> ..." : ""}`,
    ].join("\n"),
  };
}

function runDijkstraRoute(representation, graph, routePoints) {
  const start = performance.now();
  let totalCost = 0;
  let fullPath = [];

  for (let i = 0; i < routePoints.length - 1; i++) {
    const segment = representation === "matrix"
      ? matrixDijkstra(graph, routePoints[i], routePoints[i + 1])
      : dijkstra(graph, routePoints[i], routePoints[i + 1]);
    if (!segment.reachable) {
      drawGraph();
      return {
        runtimeMs: performance.now() - start,
        text: `Dijkstra route is unreachable: ${routePoints[i]} -> ${routePoints[i + 1]}`,
      };
    }
    totalCost += segment.cost;
    fullPath = fullPath.length ? fullPath.concat(segment.path.slice(1)) : segment.path;
  }

  const runtimeMs = performance.now() - start;
  state.lastPath = fullPath;
  state.lastVisited = [];
  state.lastMstEdges = [];
  drawGraph({ path: fullPath });
  els.pathSummary.textContent = `Dijkstra path cost ${formatCost(totalCost)} with ${representationLabel(representation)}.`;
  return {
    runtimeMs,
    text: [
      `Dijkstra shortest path (${representationLabel(representation)})`,
      `Required points: ${routePoints.join(" -> ")}`,
      `Total cost: ${formatCost(totalCost)}`,
      `Path nodes: ${fullPath.length}`,
      `Runtime: ${formatDuration(runtimeMs)}`,
      "",
      fullPath.join(" -> "),
    ].join("\n"),
  };
}

function runFloydRoute(routePoints) {
  const limit = 350;
  if (state.graph.vertices.length > limit) {
    drawGraph();
    els.pathSummary.textContent = "Floyd-Warshall is available for smaller uploaded graphs.";
    return {
      runtimeMs: 0,
      text: [
        "Floyd-Warshall shortest path",
        `Graph vertices: ${state.graph.vertices.length}`,
        "",
        `This browser demo blocks full Floyd-Warshall above ${limit} vertices to avoid freezing the page.`,
        "Use a smaller uploaded graph for direct Floyd-Warshall testing.",
        "For this bundled 1000-vertex graph, use Dijkstra or Global Route Optimization.",
      ].join("\n"),
    };
  }

  const start = performance.now();
  const floyd = floydWarshall(state.graph);
  let totalCost = 0;
  let fullPath = [];
  for (let i = 0; i < routePoints.length - 1; i++) {
    const segment = floydPath(floyd, routePoints[i], routePoints[i + 1]);
    if (!segment.reachable) {
      return {
        runtimeMs: performance.now() - start,
        text: `Floyd-Warshall route is unreachable: ${routePoints[i]} -> ${routePoints[i + 1]}`,
      };
    }
    totalCost += segment.cost;
    fullPath = fullPath.length ? fullPath.concat(segment.path.slice(1)) : segment.path;
  }

  const runtimeMs = performance.now() - start;
  state.lastPath = fullPath;
  state.lastVisited = [];
  state.lastMstEdges = [];
  drawGraph({ path: fullPath });
  els.pathSummary.textContent = `Floyd-Warshall path cost ${formatCost(totalCost)}.`;
  return {
    runtimeMs,
    text: [
      "Floyd-Warshall shortest path",
      `Required points: ${routePoints.join(" -> ")}`,
      `Total cost: ${formatCost(totalCost)}`,
      `Path nodes: ${fullPath.length}`,
      `Runtime: ${formatDuration(runtimeMs)}`,
      "",
      fullPath.join(" -> "),
    ].join("\n"),
  };
}

function runPrim(representation, graph, source) {
  const start = performance.now();
  const result = representation === "matrix" ? matrixPrim(graph, source) : prim(graph, source);
  const runtimeMs = performance.now() - start;
  state.lastPath = [];
  state.lastVisited = [];
  state.lastMstEdges = result.edges;
  drawGraph({ mstEdges: result.edges });
  els.pathSummary.textContent = `Prim MST weight ${formatCost(result.totalWeight)} with ${result.edges.length} edges.`;
  return {
    runtimeMs,
    text: [
      `Prim MST (${representationLabel(representation)})`,
      `Start: ${source}`,
      `Tree edges: ${result.edges.length}`,
      `Total weight: ${formatCost(result.totalWeight)}`,
      `Runtime: ${formatDuration(runtimeMs)}`,
      "",
      `Edge preview: ${result.edges.slice(0, 16).map(edge => `${edge.from}-${edge.to}(${edge.weight})`).join(", ")}${result.edges.length > 16 ? ", ..." : ""}`,
    ].join("\n"),
  };
}

function runTsp(targets) {
  const validTargets = targets.filter(id => state.graph.index.has(id));
  if (validTargets.length < 2) {
    return { runtimeMs: 0, text: "Global route optimization needs at least two valid target ids." };
  }
  if (validTargets.length > 12) {
    return { runtimeMs: 0, text: "For the browser demo, exact TSP is limited to 12 targets. Use fewer waypoint ids." };
  }

  const start = performance.now();
  const result = tspRoute(state.graph, validTargets);
  const runtimeMs = performance.now() - start;
  state.lastPath = result.path;
  state.lastVisited = [];
  state.lastMstEdges = [];
  drawGraph({ path: result.path });
  els.pathSummary.textContent = `Global route cost ${formatCost(result.cost)} across ${validTargets.length} targets.`;
  return {
    runtimeMs,
    text: [
      "Global route optimization",
      "Method: repeated Dijkstra + state-compression DP",
      `Targets: ${validTargets.join(" -> ")}`,
      `Visit order: ${result.order.join(" -> ")}`,
      `Total cost: ${formatCost(result.cost)}`,
      `Visualized path nodes: ${result.path.length}`,
      `Runtime: ${formatDuration(runtimeMs)}`,
    ].join("\n"),
  };
}

function parseCandidates(text) {
  return parseCsvRows(text).map(row => ({
    locationId: row[0],
    priorityScore: Number(row[1]),
  })).filter(row => row.locationId && Number.isFinite(row.priorityScore));
}

function parsePaths(text) {
  return parseCsvRows(text).map(row => ({
    from: row[0],
    to: row[1],
    weight: Number(row[2]),
  })).filter(row => row.from && row.to && Number.isFinite(row.weight));
}

function parseCsvRows(text) {
  return text.trim().split(/\r?\n/).slice(1)
    .map(line => line.split(",").map(part => part.trim()))
    .filter(row => row.length > 1 && row.some(Boolean));
}

function compareCandidates(a, b) {
  if (a.priorityScore !== b.priorityScore) {
    return b.priorityScore - a.priorityScore;
  }
  return a.locationId.localeCompare(b.locationId);
}

function copyCandidates(candidates) {
  return candidates.map(candidate => ({ ...candidate }));
}

function bubbleSort(list) {
  for (let end = list.length - 1; end > 0; end--) {
    let swapped = false;
    for (let i = 0; i < end; i++) {
      if (compareCandidates(list[i], list[i + 1]) > 0) {
        [list[i], list[i + 1]] = [list[i + 1], list[i]];
        swapped = true;
      }
    }
    if (!swapped) break;
  }
  return list;
}

function quickSort(list) {
  quickSortRange(list, 0, list.length - 1, false);
  return list;
}

function quickSortMedianThree(list) {
  quickSortRange(list, 0, list.length - 1, true);
  return list;
}

function quickSortRange(list, first, last, medianPivot) {
  if (last <= first) return;
  const pivotIndex = partition(list, first, last, medianPivot);
  quickSortRange(list, first, pivotIndex - 1, medianPivot);
  quickSortRange(list, pivotIndex + 1, last, medianPivot);
}

function partition(list, first, last, medianPivot) {
  if (medianPivot) {
    const middle = Math.floor((first + last) / 2);
    const median = medianIndex(list, first, middle, last);
    [list[first], list[median]] = [list[median], list[first]];
  }

  const pivot = list[first];
  let low = first + 1;
  let high = last;

  while (high > low) {
    while (low <= high && compareCandidates(list[low], pivot) <= 0) low++;
    while (low <= high && compareCandidates(list[high], pivot) > 0) high--;
    if (high > low) {
      [list[low], list[high]] = [list[high], list[low]];
    }
  }

  while (high > first && compareCandidates(list[high], pivot) >= 0) high--;
  if (compareCandidates(pivot, list[high]) > 0) {
    list[first] = list[high];
    list[high] = pivot;
    return high;
  }
  return first;
}

function medianIndex(list, first, middle, last) {
  const a = list[first];
  const b = list[middle];
  const c = list[last];
  if (compareCandidates(a, b) <= 0) {
    if (compareCandidates(b, c) <= 0) return middle;
    return compareCandidates(a, c) <= 0 ? last : first;
  }
  if (compareCandidates(a, c) <= 0) return first;
  return compareCandidates(b, c) <= 0 ? last : middle;
}

function mergeSort(list) {
  if (list.length <= 1) return list;
  const mid = Math.floor(list.length / 2);
  const left = mergeSort(list.slice(0, mid));
  const right = mergeSort(list.slice(mid));
  let i = 0;
  let j = 0;
  let k = 0;
  while (i < left.length && j < right.length) {
    list[k++] = compareCandidates(left[i], right[j]) <= 0 ? left[i++] : right[j++];
  }
  while (i < left.length) list[k++] = left[i++];
  while (j < right.length) list[k++] = right[j++];
  return list;
}

function buildGraph(edges) {
  const adjacency = new Map();
  const addVertex = id => {
    if (!adjacency.has(id)) adjacency.set(id, []);
  };
  for (const edge of edges) {
    addVertex(edge.from);
    addVertex(edge.to);
    adjacency.get(edge.from).push({ to: edge.to, weight: edge.weight });
    adjacency.get(edge.to).push({ to: edge.from, weight: edge.weight });
  }
  for (const list of adjacency.values()) {
    list.sort((a, b) => a.to.localeCompare(b.to) || a.weight - b.weight);
  }
  const vertices = [...adjacency.keys()].sort();
  const index = new Map(vertices.map((id, i) => [id, i]));
  return { adjacency, vertices, index, edges, edgeCount: edges.length };
}

function buildMatrixGraph(graph) {
  const n = graph.vertices.length;
  const matrix = Array.from({ length: n }, (_, row) =>
    Array.from({ length: n }, (_, col) => row === col ? 0 : Infinity)
  );
  for (const edge of graph.edges) {
    const from = graph.index.get(edge.from);
    const to = graph.index.get(edge.to);
    matrix[from][to] = Math.min(matrix[from][to], edge.weight);
    matrix[to][from] = Math.min(matrix[to][from], edge.weight);
  }
  return { vertices: graph.vertices, index: graph.index, matrix, edgeCount: graph.edgeCount };
}

function bfs(graph, start) {
  const visited = new Set([start]);
  const queue = [start];
  const order = [];
  while (queue.length) {
    const here = queue.shift();
    order.push(here);
    for (const edge of graph.adjacency.get(here) || []) {
      if (!visited.has(edge.to)) {
        visited.add(edge.to);
        queue.push(edge.to);
      }
    }
  }
  return { start, order };
}

function dfs(graph, start) {
  const visited = new Set();
  const order = [];
  const visit = vertex => {
    visited.add(vertex);
    order.push(vertex);
    for (const edge of graph.adjacency.get(vertex) || []) {
      if (!visited.has(edge.to)) visit(edge.to);
    }
  };
  visit(start);
  return { start, order };
}

function dijkstra(graph, source, destination) {
  const dist = new Map();
  const prev = new Map();
  for (const vertex of graph.vertices) dist.set(vertex, Infinity);
  dist.set(source, 0);

  const heap = new MinHeap();
  heap.push({ vertex: source, priority: 0 });
  while (!heap.isEmpty()) {
    const current = heap.pop();
    if (current.priority !== dist.get(current.vertex)) continue;
    if (current.vertex === destination) break;
    for (const edge of graph.adjacency.get(current.vertex) || []) {
      const nextDist = current.priority + edge.weight;
      if (nextDist < dist.get(edge.to)) {
        dist.set(edge.to, nextDist);
        prev.set(edge.to, current.vertex);
        heap.push({ vertex: edge.to, priority: nextDist });
      }
    }
  }

  if (!Number.isFinite(dist.get(destination))) {
    return { reachable: false, cost: Infinity, path: [] };
  }
  return { reachable: true, cost: dist.get(destination), path: rebuildPath(prev, source, destination) };
}

function prim(graph, source) {
  const visited = new Set([source]);
  const heap = new MinHeap();
  const edges = [];
  let totalWeight = 0;
  for (const edge of graph.adjacency.get(source) || []) {
    heap.push({ from: source, to: edge.to, weight: edge.weight, priority: edge.weight });
  }
  while (!heap.isEmpty() && visited.size < graph.vertices.length) {
    const edge = heap.pop();
    if (visited.has(edge.to)) continue;
    visited.add(edge.to);
    edges.push(edge);
    totalWeight += edge.weight;
    for (const next of graph.adjacency.get(edge.to) || []) {
      if (!visited.has(next.to)) {
        heap.push({ from: edge.to, to: next.to, weight: next.weight, priority: next.weight });
      }
    }
  }
  return { edges, totalWeight };
}

function matrixBfs(graph, start) {
  const startIndex = graph.index.get(start);
  const visited = new Set([startIndex]);
  const queue = [startIndex];
  const order = [];
  while (queue.length) {
    const row = queue.shift();
    order.push(graph.vertices[row]);
    for (let col = 0; col < graph.vertices.length; col++) {
      if (row !== col && Number.isFinite(graph.matrix[row][col]) && !visited.has(col)) {
        visited.add(col);
        queue.push(col);
      }
    }
  }
  return { start, order };
}

function matrixDfs(graph, start) {
  const visited = new Set();
  const order = [];
  const visit = row => {
    visited.add(row);
    order.push(graph.vertices[row]);
    for (let col = 0; col < graph.vertices.length; col++) {
      if (row !== col && Number.isFinite(graph.matrix[row][col]) && !visited.has(col)) visit(col);
    }
  };
  visit(graph.index.get(start));
  return { start, order };
}

function matrixDijkstra(graph, source, destination) {
  const n = graph.vertices.length;
  const sourceIndex = graph.index.get(source);
  const destinationIndex = graph.index.get(destination);
  const dist = Array(n).fill(Infinity);
  const prev = Array(n).fill(-1);
  const used = Array(n).fill(false);
  dist[sourceIndex] = 0;

  for (let step = 0; step < n; step++) {
    let current = -1;
    for (let i = 0; i < n; i++) {
      if (!used[i] && (current === -1 || dist[i] < dist[current])) current = i;
    }
    if (current === -1 || current === destinationIndex) break;
    used[current] = true;
    for (let next = 0; next < n; next++) {
      const weight = graph.matrix[current][next];
      if (used[next] || !Number.isFinite(weight)) continue;
      const candidate = dist[current] + weight;
      if (candidate < dist[next]) {
        dist[next] = candidate;
        prev[next] = current;
      }
    }
  }

  if (!Number.isFinite(dist[destinationIndex])) {
    return { reachable: false, cost: Infinity, path: [] };
  }
  const pathIndexes = [];
  for (let at = destinationIndex; at !== -1; at = prev[at]) {
    pathIndexes.push(at);
    if (at === sourceIndex) break;
  }
  return { reachable: true, cost: dist[destinationIndex], path: pathIndexes.reverse().map(i => graph.vertices[i]) };
}

function matrixPrim(graph, source) {
  const n = graph.vertices.length;
  const start = graph.index.get(source);
  const used = Array(n).fill(false);
  const minWeight = Array(n).fill(Infinity);
  const parent = Array(n).fill(-1);
  const edges = [];
  let totalWeight = 0;
  minWeight[start] = 0;

  for (let step = 0; step < n; step++) {
    let current = -1;
    for (let i = 0; i < n; i++) {
      if (!used[i] && (current === -1 || minWeight[i] < minWeight[current])) current = i;
    }
    if (current === -1 || !Number.isFinite(minWeight[current])) break;
    used[current] = true;
    if (parent[current] !== -1) {
      edges.push({
        from: graph.vertices[parent[current]],
        to: graph.vertices[current],
        weight: minWeight[current],
      });
      totalWeight += minWeight[current];
    }
    for (let next = 0; next < n; next++) {
      const weight = graph.matrix[current][next];
      if (!used[next] && Number.isFinite(weight) && weight < minWeight[next]) {
        minWeight[next] = weight;
        parent[next] = current;
      }
    }
  }
  return { edges, totalWeight };
}

function floydWarshall(graph) {
  const n = graph.vertices.length;
  const dist = Array.from({ length: n }, (_, row) =>
    Array.from({ length: n }, (_, col) => row === col ? 0 : Infinity)
  );
  const next = Array.from({ length: n }, () => Array(n).fill(-1));

  for (const edge of graph.edges) {
    const from = graph.index.get(edge.from);
    const to = graph.index.get(edge.to);
    if (edge.weight < dist[from][to]) {
      dist[from][to] = edge.weight;
      dist[to][from] = edge.weight;
      next[from][to] = to;
      next[to][from] = from;
    }
  }

  for (let k = 0; k < n; k++) {
    for (let i = 0; i < n; i++) {
      if (!Number.isFinite(dist[i][k])) continue;
      const throughK = dist[i][k];
      for (let j = 0; j < n; j++) {
        const candidate = throughK + dist[k][j];
        if (candidate < dist[i][j]) {
          dist[i][j] = candidate;
          next[i][j] = next[i][k];
        }
      }
    }
  }

  return { vertices: graph.vertices, index: graph.index, dist, next };
}

function floydPath(floyd, source, destination) {
  const from = floyd.index.get(source);
  const to = floyd.index.get(destination);
  if (from === undefined || to === undefined || floyd.next[from][to] === -1) {
    return { reachable: false, cost: Infinity, path: [] };
  }

  const path = [source];
  let current = from;
  while (current !== to) {
    current = floyd.next[current][to];
    if (current === -1) {
      return { reachable: false, cost: Infinity, path: [] };
    }
    path.push(floyd.vertices[current]);
  }
  return { reachable: true, cost: floyd.dist[from][to], path };
}

function tspRoute(graph, targets) {
  const n = targets.length;
  const segments = Array.from({ length: n }, () => Array(n).fill(null));
  const cost = Array.from({ length: n }, () => Array(n).fill(Infinity));
  for (let i = 0; i < n; i++) {
    for (let j = 0; j < n; j++) {
      if (i === j) {
        cost[i][j] = 0;
        segments[i][j] = [targets[i]];
      } else {
        const result = dijkstra(graph, targets[i], targets[j]);
        cost[i][j] = result.cost;
        segments[i][j] = result.path;
      }
    }
  }

  const stateCount = 1 << n;
  const dp = Array.from({ length: stateCount }, () => Array(n).fill(Infinity));
  const parent = Array.from({ length: stateCount }, () => Array(n).fill(-1));
  dp[1][0] = 0;
  for (let mask = 1; mask < stateCount; mask++) {
    if ((mask & 1) === 0) continue;
    for (let u = 0; u < n; u++) {
      if ((mask & (1 << u)) === 0 || !Number.isFinite(dp[mask][u])) continue;
      for (let v = 0; v < n; v++) {
        if (mask & (1 << v)) continue;
        const nextMask = mask | (1 << v);
        const candidate = dp[mask][u] + cost[u][v];
        if (candidate < dp[nextMask][v]) {
          dp[nextMask][v] = candidate;
          parent[nextMask][v] = u;
        }
      }
    }
  }

  const fullMask = stateCount - 1;
  let bestCost = Infinity;
  let last = -1;
  for (let u = 1; u < n; u++) {
    const candidate = dp[fullMask][u] + cost[u][0];
    if (candidate < bestCost) {
      bestCost = candidate;
      last = u;
    }
  }

  const reversed = [];
  let mask = fullMask;
  while (last !== -1) {
    reversed.push(last);
    const previous = parent[mask][last];
    mask &= ~(1 << last);
    last = previous;
  }
  reversed.push(0);
  const orderIndexes = reversed.reverse();
  orderIndexes.push(0);

  let visualPath = [];
  for (let i = 0; i < orderIndexes.length - 1; i++) {
    const segment = segments[orderIndexes[i]][orderIndexes[i + 1]];
    visualPath = visualPath.length ? visualPath.concat(segment.slice(1)) : [...segment];
  }

  return {
    cost: bestCost,
    order: orderIndexes.map(index => targets[index]),
    path: visualPath,
  };
}

function rebuildPath(prev, source, destination) {
  const path = [];
  for (let at = destination; at !== undefined; at = prev.get(at)) {
    path.push(at);
    if (at === source) break;
  }
  return path.reverse();
}

class MinHeap {
  constructor() {
    this.values = [];
  }

  isEmpty() {
    return this.values.length === 0;
  }

  push(value) {
    this.values.push(value);
    this.bubbleUp(this.values.length - 1);
  }

  pop() {
    const root = this.values[0];
    const end = this.values.pop();
    if (this.values.length) {
      this.values[0] = end;
      this.bubbleDown(0);
    }
    return root;
  }

  bubbleUp(index) {
    while (index > 0) {
      const parent = Math.floor((index - 1) / 2);
      if (this.values[parent].priority <= this.values[index].priority) break;
      [this.values[parent], this.values[index]] = [this.values[index], this.values[parent]];
      index = parent;
    }
  }

  bubbleDown(index) {
    while (true) {
      const left = index * 2 + 1;
      const right = left + 1;
      let smallest = index;
      if (left < this.values.length && this.values[left].priority < this.values[smallest].priority) smallest = left;
      if (right < this.values.length && this.values[right].priority < this.values[smallest].priority) smallest = right;
      if (smallest === index) break;
      [this.values[smallest], this.values[index]] = [this.values[index], this.values[smallest]];
      index = smallest;
    }
  }
}

function buildPositions() {
  state.positions.clear();
  if (!state.graph) return;
  const cols = Math.ceil(Math.sqrt(state.graph.vertices.length));
  const gap = 34;
  const margin = 40;
  state.graph.vertices.forEach((vertex, index) => {
    const row = Math.floor(index / cols);
    const col = index % cols;
    state.positions.set(vertex, {
      x: margin + col * gap + jitter(vertex, 0),
      y: margin + row * gap + jitter(vertex, 1),
    });
  });
}

function jitter(id, salt) {
  let hash = 0;
  const text = `${id}#${salt}`;
  for (let i = 0; i < text.length; i++) {
    hash = ((hash << 5) - hash + text.charCodeAt(i)) | 0;
  }
  return Math.abs(hash % 9) - 4;
}

function drawRuntimeChart(timings, selectedKey) {
  const canvas = els.runtimeChart;
  const ctx = setupCanvas(canvas);
  const width = canvas.clientWidth;
  const height = canvas.clientHeight;
  ctx.clearRect(0, 0, width, height);
  ctx.fillStyle = "#fbfcfe";
  ctx.fillRect(0, 0, width, height);

  if (!timings.length) {
    drawCenteredText(ctx, width, height, "Load data to show runtime chart");
    return;
  }

  const left = 64;
  const right = 24;
  const top = 30;
  const bottom = 66;
  const chartWidth = width - left - right;
  const chartHeight = height - top - bottom;
  const max = Math.max(...timings.map(item => item.averageMs), 0.001) * 1.18;
  const barWidth = chartWidth / timings.length * 0.58;

  ctx.strokeStyle = "#d8dee9";
  ctx.lineWidth = 1;
  for (let i = 0; i <= 4; i++) {
    const y = top + chartHeight - chartHeight * i / 4;
    ctx.beginPath();
    ctx.moveTo(left, y);
    ctx.lineTo(left + chartWidth, y);
    ctx.stroke();
    ctx.fillStyle = "#697386";
    ctx.font = "12px Segoe UI";
    ctx.fillText(formatDuration(max * i / 4), 8, y + 4);
  }

  timings.forEach((item, index) => {
    const slot = chartWidth / timings.length;
    const x = left + slot * index + (slot - barWidth) / 2;
    const barHeight = chartHeight * item.averageMs / max;
    const y = top + chartHeight - barHeight;
    ctx.fillStyle = item.key === selectedKey ? "#1f6feb" : "#7a8ca8";
    ctx.fillRect(x, y, barWidth, barHeight);
    ctx.fillStyle = "#1f2937";
    ctx.font = "12px Segoe UI";
    ctx.textAlign = "center";
    ctx.fillText(formatDuration(item.averageMs), x + barWidth / 2, Math.max(top + 12, y - 7));
    ctx.fillText(shortAlgorithmLabel(item.key), x + barWidth / 2, top + chartHeight + 26);
    ctx.fillStyle = "#697386";
    ctx.fillText(`best ${formatDuration(item.bestMs)}`, x + barWidth / 2, top + chartHeight + 44);
  });
  ctx.textAlign = "left";
}

function drawGraph(options = {}) {
  const path = options.path || [];
  const visited = options.visited || [];
  const mstEdges = options.mstEdges || [];
  const canvas = els.graphCanvas;
  const ctx = setupCanvas(canvas);
  const width = canvas.clientWidth;
  const height = canvas.clientHeight;
  ctx.clearRect(0, 0, width, height);
  ctx.fillStyle = "#fbfcfe";
  ctx.fillRect(0, 0, width, height);

  if (!state.graph) {
    drawCenteredText(ctx, width, height, "Load paths.csv to show graph visualization");
    return;
  }

  const bounds = graphBounds();
  const scale = Math.min((width - 60) / bounds.width, (height - 60) / bounds.height);
  const offsetX = (width - bounds.width * scale) / 2 - bounds.minX * scale;
  const offsetY = (height - bounds.height * scale) / 2 - bounds.minY * scale;
  const toScreen = vertex => {
    const point = state.positions.get(vertex);
    return { x: point.x * scale + offsetX, y: point.y * scale + offsetY };
  };

  ctx.lineWidth = 0.7;
  ctx.strokeStyle = "rgba(122, 140, 168, 0.22)";
  for (const edge of state.graph.edges) {
    const a = toScreen(edge.from);
    const b = toScreen(edge.to);
    ctx.beginPath();
    ctx.moveTo(a.x, a.y);
    ctx.lineTo(b.x, b.y);
    ctx.stroke();
  }

  if (mstEdges.length) {
    ctx.lineWidth = 1.2;
    ctx.strokeStyle = "rgba(22, 137, 87, 0.55)";
    for (const edge of mstEdges) {
      const a = toScreen(edge.from);
      const b = toScreen(edge.to);
      ctx.beginPath();
      ctx.moveTo(a.x, a.y);
      ctx.lineTo(b.x, b.y);
      ctx.stroke();
    }
  }

  if (path.length > 1) {
    ctx.lineWidth = 3;
    ctx.strokeStyle = "#1f6feb";
    ctx.beginPath();
    path.forEach((vertex, index) => {
      const point = toScreen(vertex);
      if (index === 0) ctx.moveTo(point.x, point.y);
      else ctx.lineTo(point.x, point.y);
    });
    ctx.stroke();
  }

  const important = new Set(path.concat(visited.slice(0, 80)));
  const topTargets = getCurrentTop10Ids();
  for (const vertex of state.graph.vertices) {
    const point = toScreen(vertex);
    const isTop = topTargets.has(vertex);
    const isPath = path.includes(vertex);
    const isVisited = important.has(vertex);
    ctx.fillStyle = isPath ? "#1f6feb" : isTop ? "#d34a4a" : isVisited ? "#168957" : "#344054";
    const radius = isPath ? 4.5 : isTop || isVisited ? 3.5 : 1.8;
    ctx.beginPath();
    ctx.arc(point.x, point.y, radius, 0, Math.PI * 2);
    ctx.fill();
  }

  drawGraphLegend(ctx, width, path.length, visited.length, mstEdges.length);
}

function graphBounds() {
  const values = [...state.positions.values()];
  const xs = values.map(point => point.x);
  const ys = values.map(point => point.y);
  const minX = Math.min(...xs);
  const maxX = Math.max(...xs);
  const minY = Math.min(...ys);
  const maxY = Math.max(...ys);
  return { minX, minY, width: maxX - minX || 1, height: maxY - minY || 1 };
}

function drawGraphLegend(ctx, width, pathCount, visitedCount, mstCount) {
  ctx.fillStyle = "rgba(255, 255, 255, 0.92)";
  ctx.fillRect(14, 14, 310, 76);
  ctx.strokeStyle = "#d8dee9";
  ctx.strokeRect(14, 14, 310, 76);
  ctx.font = "12px Segoe UI";
  ctx.fillStyle = "#1f2937";
  ctx.fillText("Red: current dataset top 10", 28, 35);
  ctx.fillText(`Blue path nodes: ${pathCount}`, 28, 54);
  ctx.fillText(`Green visited preview: ${visitedCount}; MST edges: ${mstCount}`, 28, 73);
}

function setupCanvas(canvas) {
  const ratio = window.devicePixelRatio || 1;
  const width = canvas.clientWidth;
  const height = canvas.clientHeight;
  if (canvas.width !== Math.round(width * ratio) || canvas.height !== Math.round(height * ratio)) {
    canvas.width = Math.round(width * ratio);
    canvas.height = Math.round(height * ratio);
  }
  const ctx = canvas.getContext("2d");
  ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
  return ctx;
}

function drawCenteredText(ctx, width, height, text) {
  ctx.fillStyle = "#697386";
  ctx.font = "15px Segoe UI";
  ctx.textAlign = "center";
  ctx.fillText(text, width / 2, height / 2);
  ctx.textAlign = "left";
}

function getCurrentTop10Ids() {
  const dataset = state.candidates[els.datasetSelect.value] || [];
  return new Set(mergeSort(copyCandidates(dataset)).slice(0, 10).map(candidate => candidate.locationId));
}

function parseIdList(value) {
  return value.split(",").map(item => item.trim()).filter(Boolean);
}

function shortAlgorithmLabel(key) {
  return { bubble: "Bubble", quick: "Quick", quickM3: "Quick M3", merge: "Merge" }[key] || key;
}

function representationLabel(value) {
  return value === "matrix" ? "Adjacency Matrix" : "Adjacency List";
}

function formatCost(value) {
  if (!Number.isFinite(value)) return "UNREACHABLE";
  return Number.isInteger(value) ? value.toLocaleString() : value.toFixed(2);
}

function formatDuration(ms) {
  if (!Number.isFinite(ms) || ms < 0) {
    return "0 ms";
  }
  if (ms >= 1) {
    return `${ms.toFixed(3)} ms`;
  }

  const microseconds = ms * 1000;
  if (microseconds >= 1) {
    return `${microseconds.toFixed(2)} us`;
  }

  return `${Math.max(1, Math.round(microseconds * 1000)).toLocaleString()} ns`;
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, char => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "\"": "&quot;",
    "'": "&#039;",
  }[char]));
}

window.addEventListener("resize", () => {
  drawRuntimeChart(state.lastSortTimings, DEFAULT_SORT_ALGORITHM);
  drawGraph({ path: state.lastPath, visited: state.lastVisited, mstEdges: state.lastMstEdges });
});
