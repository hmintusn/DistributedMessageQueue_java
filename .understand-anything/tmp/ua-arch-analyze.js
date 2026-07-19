#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');

const DIR_PATTERNS = {
  routes: 'api', api: 'api', controllers: 'api', endpoints: 'api', handlers: 'api',
  services: 'service', core: 'service', lib: 'service', domain: 'service', logic: 'service',
  models: 'data', db: 'data', data: 'data', persistence: 'data', repository: 'data', entities: 'data',
  components: 'ui', views: 'ui', pages: 'ui', ui: 'ui', layouts: 'ui', screens: 'ui',
  middleware: 'middleware', plugins: 'middleware', interceptors: 'middleware', guards: 'middleware',
  utils: 'utility', helpers: 'utility', common: 'utility', shared: 'utility', tools: 'utility',
  config: 'config', constants: 'config', env: 'config', settings: 'config',
  __tests__: 'test', test: 'test', tests: 'test', spec: 'test', specs: 'test',
  types: 'types', interfaces: 'types', schemas: 'types', contracts: 'types', dtos: 'types',
  hooks: 'hooks', store: 'state', state: 'state', reducers: 'state', actions: 'state', slices: 'state',
  assets: 'assets', static: 'assets', public: 'assets', migrations: 'data',
  management: 'config', commands: 'config', templatetags: 'utility', signals: 'service',
  serializers: 'api', cmd: 'entry', internal: 'service', pkg: 'utility',
  dto: 'types', request: 'types', response: 'types', entity: 'data', controller: 'api',
  routers: 'api', composables: 'service', blueprints: 'api', mailers: 'service', jobs: 'service',
  channels: 'service', bin: 'entry', docs: 'documentation', documentation: 'documentation',
  wiki: 'documentation', deploy: 'infrastructure', deployment: 'infrastructure',
  infra: 'infrastructure', infrastructure: 'infrastructure', '.github': 'ci-cd',
  '.gitlab': 'ci-cd', '.circleci': 'ci-cd', k8s: 'infrastructure', kubernetes: 'infrastructure',
  helm: 'infrastructure', charts: 'infrastructure', terraform: 'infrastructure', tf: 'infrastructure',
  docker: 'infrastructure', sql: 'data', database: 'data', schema: 'data',
  protocol: 'types', learning: 'documentation',
};

function main() {
  const inputPath = process.argv[2];
  const outputPath = process.argv[3];
  if (!inputPath || !outputPath) {
    console.error('Usage: node ua-arch-analyze.js <input.json> <output.json>');
    process.exit(1);
  }

  let data;
  try {
    data = JSON.parse(fs.readFileSync(inputPath, 'utf8'));
  } catch (e) {
    console.error('Failed to read input:', e.message);
    process.exit(1);
  }

  const fileNodes = data.fileNodes || [];
  const importEdges = data.importEdges || [];
  const allEdges = data.allEdges || [];

  const nodeById = new Map(fileNodes.map((n) => [n.id, n]));

  function getFilePath(nodeId) {
    const n = nodeById.get(nodeId);
    return n ? n.filePath : nodeId.replace(/^[^:]+:/, '');
  }

  function getNodeType(nodeId) {
    const n = nodeById.get(nodeId);
    if (n) return n.type;
    const prefix = nodeId.split(':')[0];
    return prefix === 'file' ? 'file' : prefix;
  }

  // A. Directory grouping
  const paths = fileNodes.map((n) => n.filePath.replace(/\\/g, '/'));
  let commonPrefix = '';
  if (paths.length > 0) {
    const splitPaths = paths.map((p) => p.split('/'));
    const minLen = Math.min(...splitPaths.map((s) => s.length));
    let prefixParts = [];
    for (let i = 0; i < minLen; i++) {
      const seg = splitPaths[0][i];
      if (splitPaths.every((s) => s[i] === seg)) prefixParts.push(seg);
      else break;
    }
    if (prefixParts.length > 0 && prefixParts[prefixParts.length - 1].includes('.')) {
      prefixParts.pop();
    }
    commonPrefix = prefixParts.length > 0 ? prefixParts.join('/') + '/' : '';
  }

  const directoryGroups = {};
  const fileToGroup = {};

  function assignGroup(filePath) {
    const norm = filePath.replace(/\\/g, '/');
    let rel = norm;
    if (commonPrefix && norm.startsWith(commonPrefix)) {
      rel = norm.slice(commonPrefix.length);
    }
    const parts = rel.split('/').filter(Boolean);
    if (parts.length <= 1) {
      const name = parts[0] || norm;
      if (!name.includes('.')) return name;
      // flat file at root or under prefix
      const ext = path.extname(name);
      if (/\.(test|spec)\./i.test(name) || /Test\.java$/i.test(name)) return 'test';
      return 'root';
    }
    return parts[0];
  }

  for (const node of fileNodes) {
    const group = assignGroup(node.filePath);
    if (!directoryGroups[group]) directoryGroups[group] = [];
    directoryGroups[group].push(node.id);
    fileToGroup[node.id] = group;
  }

  // If all in one group with many root files, subdivide root by extension pattern
  const groupKeys = Object.keys(directoryGroups);
  if (groupKeys.length === 1 && groupKeys[0] === 'root' && directoryGroups.root.length > 3) {
    const newGroups = {};
    for (const id of directoryGroups.root) {
      const fp = getFilePath(id);
      const base = path.basename(fp);
      let sub = 'root';
      if (/\.(test|spec)\./i.test(base) || /Test\.java$/i.test(base)) sub = 'test';
      else if (/\.md$/i.test(base)) sub = 'docs';
      else if (/\.(json|yaml|yml|toml|xml|properties|ignore)$/i.test(base)) sub = 'config';
      if (!newGroups[sub]) newGroups[sub] = [];
      newGroups[sub].push(id);
      fileToGroup[id] = sub;
    }
    Object.assign(directoryGroups, newGroups);
    delete directoryGroups.root;
  }

  // B. Node type grouping
  const nodeTypeGroups = {};
  for (const node of fileNodes) {
    const t = node.type;
    if (!nodeTypeGroups[t]) nodeTypeGroups[t] = [];
    nodeTypeGroups[t].push(node.id);
  }

  // C. Import adjacency
  const fileFanOut = {};
  const fileFanIn = {};
  for (const n of fileNodes) {
    fileFanOut[n.id] = 0;
    fileFanIn[n.id] = 0;
  }

  const importAdj = new Map();
  for (const e of importEdges) {
    if (e.type !== 'imports') continue;
    if (!importAdj.has(e.source)) importAdj.set(e.source, new Set());
    importAdj.get(e.source).add(e.target);
    fileFanOut[e.source] = (fileFanOut[e.source] || 0) + 1;
    fileFanIn[e.target] = (fileFanIn[e.target] || 0) + 1;
  }

  // D. Cross-category
  const crossMap = new Map();
  for (const e of allEdges) {
    const fromType = getNodeType(e.source);
    const toType = getNodeType(e.target);
    const key = `${fromType}|${toType}|${e.type}`;
    crossMap.set(key, (crossMap.get(key) || 0) + 1);
  }
  const crossCategoryEdges = [];
  for (const [key, count] of crossMap) {
    const [fromType, toType, edgeType] = key.split('|');
    crossCategoryEdges.push({ fromType, toType, edgeType, count });
  }

  // E. Inter-group imports
  const interGroupMap = new Map();
  for (const e of importEdges) {
    if (e.type !== 'imports') continue;
    const fromG = fileToGroup[e.source] || 'unknown';
    const toG = fileToGroup[e.target] || 'unknown';
    const key = `${fromG}->${toG}`;
    interGroupMap.set(key, (interGroupMap.get(key) || 0) + 1);
  }
  const interGroupImports = [];
  for (const [key, count] of interGroupMap) {
    const [from, to] = key.split('->');
    interGroupImports.push({ from, to, count });
  }

  // F. Intra-group density
  const intraGroupDensity = {};
  for (const group of Object.keys(directoryGroups)) {
    let internalEdges = 0;
    let totalEdges = 0;
    const members = new Set(directoryGroups[group]);
    for (const e of importEdges) {
      if (e.type !== 'imports') continue;
      const fromG = fileToGroup[e.source];
      const toG = fileToGroup[e.target];
      if (fromG === group || toG === group) totalEdges++;
      if (fromG === group && toG === group && members.has(e.source) && members.has(e.target)) {
        internalEdges++;
      }
    }
    intraGroupDensity[group] = {
      internalEdges,
      totalEdges,
      density: totalEdges > 0 ? internalEdges / totalEdges : 0,
    };
  }

  // G. Pattern matching
  const patternMatches = {};
  for (const group of Object.keys(directoryGroups)) {
    const lower = group.toLowerCase();
    if (DIR_PATTERNS[lower]) {
      patternMatches[group] = DIR_PATTERNS[lower];
      continue;
    }
    // file-level for group members
    let label = null;
    for (const id of directoryGroups[group]) {
      const fp = getFilePath(id);
      const base = path.basename(fp);
      if (/\.(test|spec)\./i.test(base) || /Test\.java$/i.test(base) || /^test_/i.test(base)) {
        label = 'test';
        break;
      }
      if (base === 'Application.java' || base === 'main.go' || base === 'manage.py') {
        label = 'entry';
        break;
      }
      if (/^Dockerfile/i.test(base) || /^docker-compose/i.test(base)) {
        label = 'infrastructure';
        break;
      }
      if (/\.md$/i.test(base)) {
        label = 'documentation';
        break;
      }
      if (/\.(graphql|gql|proto)$/i.test(base)) {
        label = 'types';
        break;
      }
    }
    if (!label && group === 'root') label = 'service';
    if (!label && group === 'common') label = 'utility';
    if (!label && group === 'protocol') label = 'types';
    patternMatches[group] = label || 'service';
  }

  // H. Deployment topology
  const infraFiles = [];
  let hasDockerfile = false;
  let hasCompose = false;
  let hasK8s = false;
  let hasTerraform = false;
  let hasCI = false;

  for (const node of fileNodes) {
    const fp = node.filePath.replace(/\\/g, '/');
    const base = path.basename(fp);
    if (/^Dockerfile/i.test(base)) {
      hasDockerfile = true;
      infraFiles.push(fp);
    }
    if (/^docker-compose/i.test(base)) {
      hasCompose = true;
      infraFiles.push(fp);
    }
    if (/\.(tf|tfvars)$/i.test(base)) {
      hasTerraform = true;
      infraFiles.push(fp);
    }
    if (fp.includes('k8s/') || fp.includes('kubernetes/') || fp.includes('helm/')) {
      hasK8s = true;
      infraFiles.push(fp);
    }
    if (fp.includes('.github/workflows') || base === '.gitlab-ci.yml' || base === 'Jenkinsfile') {
      hasCI = true;
      infraFiles.push(fp);
    }
  }

  const deploymentTopology = {
    hasDockerfile,
    hasCompose,
    hasK8s,
    hasTerraform,
    hasCI,
    infraFiles,
  };

  // I. Data pipeline
  const schemaFiles = [];
  const migrationFiles = [];
  const dataModelFiles = [];
  const apiHandlerFiles = [];

  for (const node of fileNodes) {
    const fp = node.filePath.replace(/\\/g, '/');
    const base = path.basename(fp);
    const tags = node.tags || [];
    if (/\.(sql|graphql|gql|proto|prisma)$/i.test(fp)) schemaFiles.push(fp);
    if (fp.includes('migrations/')) migrationFiles.push(fp);
    if (
      tags.includes('data-model') ||
      tags.includes('data-structure') ||
      /Queue|Topic|ConsumerGroup/i.test(base)
    ) {
      dataModelFiles.push(fp);
    }
    if (tags.includes('broker') || tags.includes('message-handler') || /Broker/i.test(base)) {
      apiHandlerFiles.push(fp);
    }
  }

  const dataPipeline = { schemaFiles, migrationFiles, dataModelFiles, apiHandlerFiles };

  // J. Doc coverage
  const groupsWithDocs = new Set();
  for (const node of fileNodes) {
    if (node.type !== 'document') continue;
    const group = fileToGroup[node.id];
    groupsWithDocs.add(group);
  }
  const totalGroups = Object.keys(directoryGroups).length;
  const undocumentedGroups = Object.keys(directoryGroups).filter((g) => !groupsWithDocs.has(g));

  const docCoverage = {
    groupsWithDocs: groupsWithDocs.size,
    totalGroups,
    coverageRatio: totalGroups > 0 ? groupsWithDocs.size / totalGroups : 0,
    undocumentedGroups,
  };

  // K. Dependency direction
  const pairCounts = new Map();
  for (const { from, to, count } of interGroupImports) {
    pairCounts.set(`${from}|${to}`, count);
  }
  const dependencyDirection = [];
  const seenPairs = new Set();
  for (const { from, to, count } of interGroupImports) {
    const rev = pairCounts.get(`${to}|${from}`) || 0;
    const pairKey = [from, to].sort().join('|');
    if (seenPairs.has(pairKey)) continue;
    seenPairs.add(pairKey);
    if (count > rev) {
      dependencyDirection.push({ dependent: from, dependsOn: to });
    } else if (rev > count) {
      dependencyDirection.push({ dependent: to, dependsOn: from });
    } else if (count > 0) {
      dependencyDirection.push({ dependent: from, dependsOn: to });
    }
  }

  const filesPerGroup = {};
  for (const [g, ids] of Object.entries(directoryGroups)) {
    filesPerGroup[g] = ids.length;
  }

  const nodeTypeCounts = {};
  for (const [t, ids] of Object.entries(nodeTypeGroups)) {
    nodeTypeCounts[t] = ids.length;
  }

  const result = {
    scriptCompleted: true,
    directoryGroups,
    nodeTypeGroups,
    crossCategoryEdges,
    interGroupImports,
    intraGroupDensity,
    patternMatches,
    deploymentTopology,
    dataPipeline,
    docCoverage,
    dependencyDirection,
    fileStats: {
      totalFileNodes: fileNodes.length,
      filesPerGroup,
      nodeTypeCounts,
    },
    fileFanIn,
    fileFanOut,
    commonPrefix,
    fileToGroup,
  };

  try {
    fs.writeFileSync(outputPath, JSON.stringify(result, null, 2), 'utf8');
  } catch (e) {
    console.error('Failed to write output:', e.message);
    process.exit(1);
  }
}

main();
