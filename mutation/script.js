const REPORTS_MANIFEST_PATH = './reports.json';

function createReportCard(report) {
    const card = document.createElement('a');
    card.className = 'card';
    card.href = report.url;
    card.target = '_blank';
    card.rel = 'noopener noreferrer';

    const title = document.createElement('h2');
    title.textContent = report.name;

    const description = document.createElement('p');
    description.textContent = 'Open PIT report';

    card.appendChild(title);
    card.appendChild(description);
    return card;
}

function normalizeReportEntry(entry) {
    if (typeof entry === 'string') {
        return {
            name: entry,
            url: `../pit/${encodeURIComponent(entry)}/overlay/target/pit-reports/index.html`,
        };
    }

    if (!entry || typeof entry !== 'object') {
        return null;
    }

    const name = typeof entry.name === 'string' ? entry.name : null;
    const url = typeof entry.url === 'string' ? entry.url : null;
    if (!name || !url) {
        return null;
    }

    return {name, url};
}

function sortReports(reports) {
    return [...reports].sort((a, b) => a.name.localeCompare(b.name, undefined, {numeric: true, sensitivity: 'base'}));
}

async function loadReportsFromManifest() {
    const response = await fetch(REPORTS_MANIFEST_PATH, {cache: 'no-store'});
    if (!response.ok) {
        throw new Error(`Could not load reports manifest (${response.status})`);
    }

    const data = await response.json();
    const rawReports = Array.isArray(data) ? data : data.reports;
    if (!Array.isArray(rawReports)) {
        return [];
    }

    return rawReports.map(normalizeReportEntry).filter(Boolean);
}

async function renderMutationReports() {
    const status = document.getElementById('reports-status');
    const grid = document.getElementById('reports-grid');

    if (!status || !grid) {
        return;
    }

    let reports;
    try {
        reports = sortReports(await loadReportsFromManifest());
    } catch (error) {
        console.warn('Could not load mutation reports manifest:', error);
        status.textContent = 'Mutation reports manifest not found. Please generate mutation/reports.json from CI/CD.';
        return;
    }

    if (reports.length === 0) {
        status.textContent = 'No mutation report published yet.';
        return;
    }

    status.textContent = `${reports.length} PIT report(s) available.`;

    reports.forEach((report) => {
        grid.appendChild(createReportCard(report));
    });
}

renderMutationReports().then(() => console.log('Mutation reports loaded'));

