const PIT_ROOT = '../pit';
const PIT_REPORT_SUFFIX = 'overlay/target/pit-reports/index.html';

function buildReportUrl(folderName) {
    return `${PIT_ROOT}/${encodeURIComponent(folderName)}/${PIT_REPORT_SUFFIX}`;
}

function createReportCard(folderName) {
    const card = document.createElement('a');
    card.className = 'card';
    card.href = buildReportUrl(folderName);
    card.target = '_blank';
    card.rel = 'noopener noreferrer';

    const title = document.createElement('h2');
    title.textContent = folderName;

    const description = document.createElement('p');
    description.textContent = 'Open PIT report';

    card.appendChild(title);
    card.appendChild(description);
    return card;
}

function sortFolders(folders) {
    return [...folders].sort((a, b) => a.localeCompare(b, undefined, {numeric: true, sensitivity: 'base'}));
}

async function scanFromDirectoryIndex() {
    const response = await fetch(`${PIT_ROOT}/`, {cache: 'no-store'});
    if (!response.ok) {
        throw new Error(`Directory listing request failed (${response.status})`);
    }

    const html = await response.text();
    const parser = new DOMParser();
    const doc = parser.parseFromString(html, 'text/html');
    const anchors = [...doc.querySelectorAll('a[href]')];

    const folders = anchors
        .map((link) => link.getAttribute('href') || '')
        .map((href) => href.replace(/^\/+/, '').replace(/\/$/, ''))
        .filter((name) => name && name !== '.' && name !== '..' && !name.includes('.'));

    return [...new Set(folders)];
}

function getGitHubRepoInfo() {
    if (!window.location.hostname.endsWith('github.io')) {
        return null;
    }

    const owner = window.location.hostname.split('.')[0];
    const [repo] = window.location.pathname.split('/').filter(Boolean);
    if (!owner || !repo) {
        return null;
    }

    return {owner, repo};
}

async function scanFromGitHubApi() {
    const repoInfo = getGitHubRepoInfo();
    if (!repoInfo) {
        return [];
    }

    const apiUrl = `https://api.github.com/repos/${repoInfo.owner}/${repoInfo.repo}/contents/pit`;
    const response = await fetch(apiUrl, {
        headers: {Accept: 'application/vnd.github+json'},
    });

    if (!response.ok) {
        throw new Error(`GitHub API request failed (${response.status})`);
    }

    const entries = await response.json();
    if (!Array.isArray(entries)) {
        return [];
    }

    return entries
        .filter((entry) => entry && entry.type === 'dir' && typeof entry.name === 'string')
        .map((entry) => entry.name);
}

async function loadPitFolders() {
    try {
        const folders = await scanFromDirectoryIndex();
        if (folders.length > 0) {
            return folders;
        }
    } catch (error) {
        console.warn('Could not scan ../pit through directory listing:', error);
    }

    try {
        return await scanFromGitHubApi();
    } catch (error) {
        console.warn('Could not scan pit via GitHub API:', error);
        return [];
    }
}

async function renderMutationReports() {
    const status = document.getElementById('reports-status');
    const grid = document.getElementById('reports-grid');

    if (!status || !grid) {
        return;
    }

    const folders = sortFolders(await loadPitFolders());
    if (folders.length === 0) {
        status.textContent = 'No PIT report folder found in ../pit.';
        return;
    }

    status.textContent = `${folders.length} PIT report folder(s) found.`;

    folders.forEach((folderName) => {
        grid.appendChild(createReportCard(folderName));
    });
}

renderMutationReports().then(() => console.log('Mutation reports loaded'));

