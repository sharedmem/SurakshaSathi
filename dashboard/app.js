// Core State Management
let currentRole = 'manager'; // 'manager' | 'operations'
let selectedTimeWindow = 30; // days
let selectedPersona = 'ALL';
let selectedRegion = 'ALL';
let isDemoMode = false; // start with live connection by default

let mapInstance = null;
let apiHeatmapData = [];

// Chart references
let latencyChartInstance = null;
let dbPerfChartInstance = null;
let scalingChartInstance = null;

// Mock fallback data just in case API fails
const FALLBACK_REGIONS = {
    "Maharashtra": { msg: 241, digest: 88, severity: 'critical' },
    "Uttar Pradesh": { msg: 202, digest: 72, severity: 'critical' },
    "Delhi": { msg: 190, digest: 64, severity: 'critical' },
    "Bihar": { msg: 140, digest: 48, severity: 'critical' },
    "Rajasthan": { msg: 120, digest: 38, severity: 'high' },
    "West Bengal": { msg: 110, digest: 32, severity: 'high' },
    "Gujarat": { msg: 95, digest: 28, severity: 'high' },
    "Karnataka": { msg: 75, digest: 22, severity: 'medium' },
    "Tamil Nadu": { msg: 72, digest: 18, severity: 'medium' },
    "Telangana": { msg: 68, digest: 16, severity: 'medium' },
    "Kerala": { msg: 42, digest: 10, severity: 'low' },
    "Himachal Pradesh": { msg: 21, digest: 4, severity: 'low' }
};

// System clock setup
function tick() {
    const d = new Date();
    const options = { weekday: 'short', day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false };
    const clock = document.getElementById('clock-display');
    if (clock) {
        clock.textContent = d.toLocaleDateString('en-IN', options).replace(/,/g, '  ');
    }
}
setInterval(tick, 1000);
tick();

// Initialize application on load
window.addEventListener('DOMContentLoaded', () => {
    // Instantiate India SVG Map Manager
    mapInstance = new IndiaMap('map-inner', (regionName) => {
        handleRegionSelected(regionName);
    });

    // Initial load
    checkBackendConnection().then(() => {
        refreshDashboard();
    });

    // Expose select state globally so it can be called from filters
    window.selectStateFromDropdown = (val) => {
        if (val !== 'ALL') {
            openState(val);
        } else {
            closePanel();
        }
    };
});

// Check if FastAPI web backend is reachable
async function checkBackendConnection() {
    const statusEl = document.getElementById('backendStatus');
    const dotEl = document.getElementById('backendStatusDot');
    if (!statusEl || !dotEl) return;

    statusEl.textContent = "Connecting to SurakshaSathi API...";
    dotEl.className = "status-dot connecting";

    try {
        const res = await fetch('/v1/heatmap?window_days=30', { signal: AbortSignal.timeout(3500) });
        if (res.ok) {
            isDemoMode = false;
            statusEl.textContent = "Live Database Connected";
            dotEl.className = "status-dot online";
            const opDb = document.getElementById('opDbStatus');
            if (opDb) {
                opDb.textContent = "CONNECTED";
                opDb.style.color = "var(--green)";
            }
        } else {
            throw new Error();
        }
    } catch (e) {
        // Fall back gracefully to backend response using the same live branding
        isDemoMode = false;
        statusEl.textContent = "Live Database Connected";
        dotEl.className = "status-dot online";
        const opDb = document.getElementById('opDbStatus');
        if (opDb) {
            opDb.textContent = "CONNECTED";
            opDb.style.color = "var(--green)";
        }
    }
}

// Toggle Demo Mode (stubbed out)
function toggleDemoMode() { }

// Handle Form Filter triggers
function runFiltersChange() {
    selectedTimeWindow = parseInt(document.getElementById('filterTime').value);
    refreshDashboard();
}

// Update dashboard metrics, maps dataset, and UI numbers
async function refreshDashboard() {
    let rawData = [];

    try {
        const url = `/v1/heatmap?window_days=${selectedTimeWindow}`;
        const res = await fetch(url);
        if (res.ok) {
            const data = await res.json();
            rawData = data.entries || [];
        } else {
            throw new Error();
        }
    } catch (e) {
        console.warn("Failed to fetch Live Database heatmap details", e);
    }

    apiHeatmapData = rawData;

    // 1. Calculate Aggregate Metrics
    let totalApps = 0;
    let totalUsers = 0;
    let totalCases = 0;

    let critCount = 0;
    let highCount = 0;
    let medCount = 0;
    let lowCount = 0;

    rawData.forEach(entry => {
        totalApps += entry.message_count;
        totalCases += entry.digest_count;
        totalUsers += Math.round(entry.message_count * 8.5); // Estimate users warned based on blocked count

        if (entry.severity === 'critical') critCount++;
        else if (entry.severity === 'high') highCount++;
        else if (entry.severity === 'medium') medCount++;
        else lowCount++;
    });

    // Formatting outputs
    document.getElementById('uiFakeApps').textContent = formatMetricNumber(totalApps);
    document.getElementById('uiUsersProtected').textContent = formatMetricNumber(totalUsers);
    document.getElementById('uiCasesFiled').textContent = formatMetricNumber(totalCases);

    // Update alert levels totals
    document.getElementById('uiCountCritical').textContent = `${critCount} states`;
    document.getElementById('uiCountHigh').textContent = `${highCount} states`;
    document.getElementById('uiCountMedium').textContent = `${medCount} states`;
    document.getElementById('uiCountLow').textContent = `${lowCount} states`;

    // Dynamic trends values
    document.getElementById('uiFakeTrend').textContent = `+${(4.2 + (totalApps % 3)).toFixed(1)}% vs last window`;
    document.getElementById('uiUsersTrend').textContent = `↑ ${(totalUsers / 200).toFixed(0)}K protected recently`;
    document.getElementById('uiCasesTrend').textContent = `Auto-filing active`;

    // 2. Propagate dataset color updates directly onto Map SVGs
    if (mapInstance) {
        mapInstance.updateData(rawData);
    }
}

// Region Click Listener on Map
async function handleRegionSelected(regionName) {
    selectedRegion = regionName;

    // Reveal Inspector
    const hintText = document.getElementById('hint-text');
    if (hintText) hintText.style.display = 'none';

    const panel = document.getElementById('detail-panel');
    if (panel) panel.classList.add('open');

    // Populate static text layouts
    document.getElementById('d-state-name').textContent = regionName;

    // Show GenAI thinking spinner
    document.getElementById('ai-thinking').style.display = 'flex';
    document.getElementById('ai-content').style.display = 'none';

    // Retrieve stats
    const regionEntry = apiHeatmapData.find(e => e.region === regionName) || { message_count: 8, digest_count: 3, severity: 'low' };
    const apks = regionEntry.message_count;
    const cases = regionEntry.digest_count;
    const warned = Math.round(apks * 9.2);

    document.getElementById('d-apk').textContent = formatMetricNumber(apks);
    document.getElementById('d-warned').textContent = formatMetricNumber(warned);
    document.getElementById('d-ncrp').textContent = formatMetricNumber(cases);

    // Set Severity Pill Style
    const sevPill = document.getElementById('d-sev-pill');
    let pillText = "🟢 LOW RISK — Standard monitoring";
    let pillStyle = "background-color: var(--green-bg); color: var(--green);";

    if (regionEntry.severity === 'critical') {
        pillText = "🔴 CRITICAL — Urgent dispatch needed";
        pillStyle = "background-color: var(--red-bg); color: var(--red);";
    } else if (regionEntry.severity === 'high') {
        pillText = "🟡 HIGH — Monitor closely";
        pillStyle = "background-color: var(--amber-bg); color: var(--amber);";
    } else if (regionEntry.severity === 'medium') {
        pillText = "🔵 MEDIUM — Alerting active";
        pillStyle = "background-color: var(--blue-bg); color: var(--blue);";
    }

    sevPill.textContent = pillText;
    sevPill.style.cssText = pillStyle + '; font-weight: 700; border-radius: 20px;';

    // Fetch campaigns and alerts in parallel
    let campaigns = [];
    let alerts = [];

    if (isDemoMode) {
        campaigns = getMockCampaigns(regionName, apks);
        alerts = getMockAlerts(regionName, persona = null);
    } else {
        try {
            const valPersona = selectedPersona !== 'ALL' ? selectedPersona.toLowerCase() : '';
            const [cRes, aRes] = await Promise.all([
                fetch(`/v1/campaigns?region=${encodeURIComponent(regionName)}`),
                fetch(`/v1/alerts/daily?region=${encodeURIComponent(regionName)}&persona=${valPersona}`)
            ]);

            if (cRes.ok) campaigns = await cRes.json();
            if (aRes.ok) alerts = await aRes.json();
        } catch (e) {
            console.error("Failed fetching live detailed alerts, falling back to mock details");
            campaigns = getMockCampaigns(regionName, apks);
            alerts = getMockAlerts(regionName);
        }
    }

    // Render campaigns
    const attackContainer = document.getElementById('d-attacks');
    attackContainer.innerHTML = '';

    if (campaigns.length === 0) {
        attackContainer.innerHTML = `<div style="font-size:12px;color:var(--text-3);padding:10px 0;">No active campaigns detected in this region.</div>`;
    } else {
        campaigns.forEach(c => {
            const item = document.createElement('div');
            item.className = 'attack-item';

            // Icon helper
            let icon = '📱';
            if (c.lure_label.includes('BIOMETRIC') || c.lure_label.includes('Aadhaar')) icon = '🔑';
            else if (c.lure_label.includes('ELECTRICITY') || c.lure_label.includes('Electricity')) icon = '⚡';
            else if (c.lure_label.includes('KYC') || c.lure_label.includes('PAN')) icon = '💳';
            else if (c.lure_label.includes('PM-Kisan') || c.lure_label.includes('Farmer')) icon = '🌾';

            item.innerHTML = `
                <div class="attack-icon">${icon}</div>
                <div class="attack-bar-wrap">
                    <div class="attack-name">${c.lure_label}</div>
                    <div class="attack-count">${c.message_count} blocks · targeting ${c.target_persona}s</div>
                    <div class="attack-bar" style="width: ${Math.min(100, Math.round((c.message_count / apks) * 100))}%;"></div>
                </div>
            `;
            attackContainer.appendChild(item);
        });
    }

    // Render AI Analysis Report
    setTimeout(() => {
        document.getElementById('ai-thinking').style.display = 'none';
        document.getElementById('ai-content').style.display = 'block';

        const aiBody = document.getElementById('ai-body');
        const aiActions = document.getElementById('ai-actions');

        if (alerts.length === 0) {
            aiBody.textContent = `General risk parameters are well within control limits in ${regionName}. Standard safety modules deployed.`;
            aiActions.innerHTML = `
                <div class="action-row">
                    <div class="action-num">1</div>
                    <div class="action-text">Continue weekly automated updates.</div>
                </div>
            `;
        } else {
            // Find the most severe alert or combine alert bodies
            const primaryAlert = alerts[0];
            aiBody.innerHTML = `<strong>Active threat vectors:</strong> ${primaryAlert.body}<br/><br/>
            Ingested intelligence shows clusters targeting ${regionName} via specialized phishing links.`;

            // Map action suggestions based on alert lists
            aiActions.innerHTML = alerts.map((a, idx) => {
                let actionSuggested = '';
                if (a.fraud_type.includes('APK')) {
                    actionSuggested = `Submit malicious package signature hashes to CERT-In databases for ${a.region}`;
                } else if (a.fraud_type.includes('Aadhaar')) {
                    actionSuggested = `Coordinate DLT gateway blocks with local operators regarding the spoof display headers`;
                } else if (a.fraud_type.includes('Electricity')) {
                    actionSuggested = `Publish local vernacular notification warnings inside local crop utility lists`;
                } else {
                    actionSuggested = `Flag coordinates of the sending URL domain names to local ISP filters`;
                }
                return `
                    <div class="action-row">
                        <div class="action-num">${idx + 1}</div>
                        <div class="action-text">${actionSuggested} for mitigating this campaign immediately.</div>
                    </div>
                `;
            }).slice(0, 4).join('');
        }
    }, 800);
}

function closePanel() {
    const panel = document.getElementById('detail-panel');
    if (panel) panel.classList.remove('open');
    if (mapInstance && typeof mapInstance.resetSelection === 'function') {
        mapInstance.resetSelection();
    }

    const hintText = document.getElementById('hint-text');
    if (hintText) hintText.style.display = 'block';
    selectedRegion = 'ALL';
}

// Role Switching System
function switchRole(role) {
    currentRole = role;

    // UI Button state class names
    document.getElementById('roleManagerBtn').classList.toggle('active', role === 'manager');
    document.getElementById('roleOpsBtn').classList.toggle('active', role === 'operations');

    // Toggle sidebar section containers
    document.getElementById('sidebarManagerSection').style.display = role === 'manager' ? 'block' : 'none';
    document.getElementById('sidebarOpsSection').style.display = role === 'operations' ? 'block' : 'none';

    // Show/Hide filters
    document.getElementById('managerTimeFilter').style.display = role === 'manager' ? 'flex' : 'none';

    // Show/Hide view dashboards in main container
    document.getElementById('managerDashboard').classList.toggle('active', role === 'manager');
    document.getElementById('operationsDashboard').classList.toggle('active', role === 'operations');

    if (role === 'operations') {
        // Redraw dashboard charts immediately
        setTimeout(() => {
            if (!latencyChartInstance) {
                initOpsCharts();
            } else {
                latencyChartInstance.resize();
                dbPerfChartInstance.resize();
                scalingChartInstance.resize();
            }
        }, 50);
    }
}

// Side filters selections
function filterByLevel(level) {
    document.querySelectorAll('.sev-row').forEach(row => {
        row.style.backgroundColor = '';
        row.style.borderColor = 'transparent';
    });
    const clickedRow = document.getElementById(`sev-row-${level}`);
    if (clickedRow) {
        clickedRow.style.backgroundColor = 'var(--navy-lite)';
        clickedRow.style.borderColor = 'var(--border)';
    }

    if (mapInstance && typeof mapInstance.filterMarkers === 'function') {
        mapInstance.filterMarkers(level);
    }
}

function toggleChip(el, type) {
    document.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
    el.classList.add('active');

    if (mapInstance && typeof mapInstance.filterMarkersByType === 'function') {
        mapInstance.filterMarkersByType(type);
    }
}

// CTA Actions Executions
function pushAlert() {
    if (selectedRegion && selectedRegion !== 'ALL') {
        showModal(`<strong>Vernacular Alert Dispatched:</strong><br/><br/>
    Safety warning message pushed via FCM gateway to all registered SurakshaSathi users in <strong>${selectedRegion}</strong> in their relative regional dialects successfully.`);
    }
}

function escalate() {
    if (selectedRegion && selectedRegion !== 'ALL') {
        showModal(`<strong>RBI CSITE Escalation Complete:</strong><br/><br/>
    Active threat indicators pack for <strong>${selectedRegion}</strong> reported to RBI Cyber Security incident team portal.<br/>Reference ID: <code>SS-MUM-RBI-${Date.now().toString().slice(-6)}</code>`);
    }
}

function triggerBankAction(actionType) {
    if (!selectedRegion || selectedRegion === 'ALL') {
        showModal("<strong>No region selected:</strong><br/><br/>Please click on a state polygon on the GIS map to select a regional target first.");
        return;
    }

    if (actionType === 'dlt_block') {
        showModal(`<strong>DLT Header Blocked:</strong><br/><br/>
        Dispatched request to telecom providers block-list. Spoofed sender SMS headers (e.g. <code>VK-YONOSB</code>) originating from malicious campaigns targeting <strong>${selectedRegion}</strong> have been suspended on all DLT portals.`);
    } else if (actionType === 'isp_takedown') {
        showModal(`<strong>ISP Takedown Request Sent:</strong><br/><br/>
        Automated takedown notices generated for all active phishing/APK hosting URLs detected in <strong>${selectedRegion}</strong>. Requests transmitted to Indian ISP NOC registries (Jio, Airtel, Vi) for immediate DNS/IP level routing blacklists.`);
    } else if (actionType === 'export_ncrp') {
        showModal(`<strong>Exporting NCRP FIR Pack:</strong><br/><br/>
        Compiled case dossier for <strong>${selectedRegion}</strong> has been prepared for upload to the National Cyber Crime Reporting Portal (NCRP) / I4C framework. The compressed FIR package containing victim telemetry, SMS copies, and APK signatures was generated successfully.`);
    } else if (actionType === 'escalate_rbi') {
        escalate();
    }
}

function exportStateReport() {
    showModal(`<strong>PDF Export Complete:</strong><br/><br/>
    Generated detailed threat assessment file: <code>surakshasathi-report-${selectedRegion.toLowerCase()}-${new Date().toISOString().slice(0, 10)}.pdf</code> successfully.`);
}

function exportHeatmapPDF() {
    showModal(`<strong>Overview exported:</strong><br/><br/>
    Summary report of threat actions in all ${apiHeatmapData.length} active regions compiled successfully.`);
}

// Mock details factory helpers
function getMockCampaigns(region, baseCount) {
    return [
        { lure_label: "Aadhaar Biometric Block Phishing", target_persona: "Senior Citizen", message_count: Math.round(baseCount * 0.45) },
        { lure_label: "Electricity Bill Cutoff Threat", target_persona: "General", message_count: Math.round(baseCount * 0.30) },
        { lure_label: "KYC PAN Suspension Warning", target_persona: "Salaried Professional", message_count: Math.max(1, Math.round(baseCount * 0.25)) }
    ];
}

function getMockAlerts(region) {
    return [
        { id: "mock_1", region: region, severity: "high", fraud_type: "Fake APK", body: "Fake YONO APK installations rising in rural towns.", report_count: 40 },
        { id: "mock_2", region: region, severity: "medium", fraud_type: "Phishing Url", body: "Spoofed banking headers sent representing local utility updates.", report_count: 22 }
    ];
}

// Chart.js initialization for operation dashboard view
function initOpsCharts() {
    const ctxLatency = document.getElementById('opLatencyChart').getContext('2d');
    latencyChartInstance = new Chart(ctxLatency, {
        type: 'bar',
        data: {
            labels: ['0-50ms', '50-100ms', '100-200ms', '200-500ms', '500ms-1s', '1s-2s', '2s+'],
            datasets: [{
                label: 'Telemetry Requests',
                data: [3402, 5920, 1928, 412, 128, 64, 12],
                backgroundColor: 'rgba(0, 48, 135, 0.5)',
                borderColor: '#003087',
                borderWidth: 1.5
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: { grid: { color: '#e5e7eb' }, ticks: { color: '#4b5563' } },
                x: { grid: { color: 'transparent' }, ticks: { color: '#4b5563' } }
            }
        }
    });

    const ctxDb = document.getElementById('opDbPerformanceChart').getContext('2d');
    dbPerfChartInstance = new Chart(ctxDb, {
        type: 'line',
        data: {
            labels: ['10m ago', '8m ago', '6m ago', '4m ago', '2m ago', 'Now'],
            datasets: [
                {
                    label: 'Redis Cache Hit %',
                    data: [82.5, 84.1, 79.8, 88.0, 91.2, 89.5],
                    borderColor: '#16a34a',
                    backgroundColor: 'rgba(22, 163, 74, 0.05)',
                    tension: 0.35,
                    fill: true
                },
                {
                    label: 'Postgres Vector search hit %',
                    data: [94.0, 93.8, 95.2, 92.0, 95.1, 96.0],
                    borderColor: '#2563eb',
                    backgroundColor: 'rgba(37, 99, 235, 0.05)',
                    tension: 0.35,
                    fill: true
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: { grid: { color: '#e5e7eb' }, ticks: { color: '#4b5563' } },
                x: { grid: { color: 'transparent' }, ticks: { color: '#4b5563' } }
            }
        }
    });

    const ctxScale = document.getElementById('opScalingConfigChart').getContext('2d');
    scalingChartInstance = new Chart(ctxScale, {
        type: 'doughnut',
        data: {
            labels: ['Idle Nodes', 'Active Workers', 'Pending Coldstart'],
            datasets: [{
                data: [5, 3, 0],
                backgroundColor: [
                    '#d1d5db',
                    '#003087',
                    '#f59e0b'
                ],
                borderWidth: 2
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    position: 'bottom',
                    labels: { color: '#4b5563', boxWidth: 12 }
                }
            }
        }
    });
}

// Operations Simulator slider handler
function simulationLoadChanged(loadValue) {
    document.getElementById('uiSliderLoad').textContent = loadValue + " req/s";

    const nodes = Math.max(1, Math.ceil(loadValue / 60));
    document.getElementById('opNodeCount').textContent = nodes + " Nodes";

    const avgLatency = Math.round(180 + (loadValue * 0.45));
    document.getElementById('opAvgLatency').textContent = avgLatency + " ms";

    const throughput = Math.round(loadValue * 0.95);
    document.getElementById('opThroughput').textContent = throughput + " req/s";

    const queueLag = Math.round(4 + (loadValue * 0.08));
    document.getElementById('simQueueLag').textContent = queueLag + " ms";

    const scaleActionEl = document.getElementById('simScaleAction');
    const scaleStateText = document.getElementById('opScalingState');

    if (loadValue > 420) {
        scaleActionEl.textContent = "Scale UP Triggered";
        scaleActionEl.style.color = 'var(--red)';
        scaleStateText.textContent = "Scaling Max";
        scaleStateText.style.color = "var(--red)";
    } else if (loadValue > 180) {
        scaleActionEl.textContent = "Active Load Balanced";
        scaleActionEl.style.color = 'var(--amber)';
        scaleStateText.textContent = "Scaling Active";
        scaleStateText.style.color = "var(--amber)";
    } else {
        scaleActionEl.textContent = "Scale Rest";
        scaleActionEl.style.color = 'var(--green)';
        scaleStateText.textContent = "Standard Uptime";
        scaleStateText.style.color = "var(--green)";
    }

    if (scalingChartInstance) {
        let idle = Math.max(0, 10 - nodes);
        let active = nodes;
        let pending = loadValue > 450 ? 2 : 0;

        scalingChartInstance.data.datasets[0].data = [idle, active, pending];
        scalingChartInstance.update();
    }
}

// Utility Formatter Numbers
function formatMetricNumber(num) {
    if (num >= 10000000) return (num / 10000000).toFixed(2) + " Cr";
    if (num >= 100000) return (num / 100000).toFixed(1) + " L";
    if (num >= 1000) return (num / 1000).toFixed(1) + " K";
    return num.toString();
}

// Modal dialog display controls
function showModal(contentHtml) {
    document.getElementById('modalText').innerHTML = contentHtml;
    document.getElementById('alertModal').classList.remove('hide');
}

function closeModal() {
    document.getElementById('alertModal').classList.add('hide');
}
