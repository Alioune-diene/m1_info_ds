const SECTIONS = {
    'link-javadoc': 'javadoc/index.html',
    'link-allure': 'allure/index.html',
    'link-mutation': 'mutation/index.html',
};

async function loadMetrics() {
    try {
        const res = await fetch('./metrics.json');
        const data = await res.json();

        document.getElementById('lastUpdated').textContent = data.timestamp ? new Date(data.timestamp * 1000).toLocaleString() : '--';
    } catch {
        console.warn('metrics.json not available');
    }
}

function updateLinks() {
    for (const [id, path] of Object.entries(SECTIONS)) {
        const el = document.getElementById(id);
        if (el) el.href = `./${path}`;
    }
}

updateLinks();
loadMetrics().then(() => console.log('Metrics loaded'));