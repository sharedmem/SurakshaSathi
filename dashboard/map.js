// Leaflet GIS Map Manager for SurakshaSathi (Choropleth Shaded Map)

class IndiaMap {
    constructor(containerId, onRegionSelect) {
        this.containerId = 'map'; // Leaflet mounts to the ID 'map'
        this.onRegionSelect = onRegionSelect;
        this.map = null;
        this.geoJsonData = null;
        this.geoJsonLayer = null;
        this.regionsData = [];
        this.selectedRegionName = null;
        this.init();
    }

    init() {
        console.log("Initializing Leaflet GIS India Map...");

        // Initialize Leaflet map instance centered in India
        this.map = L.map(this.containerId, {
            center: [21.8467, 79.9462],
            zoom: 5,
            minZoom: 4,
            maxZoom: 9,
            zoomControl: true,
            scrollWheelZoom: true
        });

        // Add CartoDB Positron style tile layer (sleek light theme)
        L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>',
            subdomains: 'abcd',
            maxZoom: 20
        }).addTo(this.map);

        console.log("Leaflet Cartography Layer Added. Fetching States GeoJSON...");

        // Fetch India States GeoJSON
        fetch('https://raw.githubusercontent.com/india-in-data/india-states-2019/master/india_states.geojson')
            .then(res => {
                if (!res.ok) throw new Error("HTTP error " + res.status);
                return res.json();
            })
            .then(data => {
                this.geoJsonData = data;
                console.log("India States GeoJSON successfully parsed.");
                // If updateData was called before fetch finished, redraw now
                if (this.regionsData.length > 0) {
                    this.renderGeoJson();
                }
            })
            .catch(err => {
                console.error("Failed to load India states GeoJSON:", err);
            });
    }

    // Normalizes state names for accurate dataset matching
    normalizeStateName(name) {
        if (!name) return "";
        let n = name.toLowerCase().trim();
        n = n.replace(/&/g, "and");
        if (n === "nct of delhi" || n === "delhi") return "delhi";
        if (n === "orissa") return "odisha";
        if (n === "uttaranchal") return "uttarakhand";
        if (n === "pondicherry") return "puducherry";
        if (n === "jammu and kashmir" || n === "jammu & kashmir") return "jammu & kashmir";
        return n;
    }

    getSeverityColor(severity) {
        const colors = {
            critical: '#EF4444', // Red
            high: '#F59E0B',     // Orange
            medium: '#2563EB',   // Blue
            low: '#10B981',      // Green
            none: '#9CA3AF'      // Grey
        };
        return colors[severity] || colors.none;
    }

    renderGeoJson() {
        if (!this.geoJsonData) return;

        // Clear existing layer
        if (this.geoJsonLayer) {
            this.map.removeLayer(this.geoJsonLayer);
        }

        const self = this;

        this.geoJsonLayer = L.geoJSON(this.geoJsonData, {
            style: (feature) => {
                const stateName = feature.properties.ST_NM;
                const normName = self.normalizeStateName(stateName);

                // Find matching entry from regionsData
                const entry = self.regionsData.find(e => self.normalizeStateName(e.region) === normName);
                const severity = entry ? entry.severity : 'none';
                const color = self.getSeverityColor(severity);

                // Selected state has distinct outline border
                const isSelected = self.selectedRegionName && self.normalizeStateName(self.selectedRegionName) === normName;

                return {
                    fillColor: color,
                    weight: isSelected ? 3.5 : 1.5,
                    opacity: 1,
                    color: isSelected ? '#1E3A8A' : '#CBD5E1', // Border outline color (Slate-200/Slate-300 or Dark Navy)
                    fillOpacity: isSelected ? 0.75 : (entry ? 0.55 : 0.25)
                };
            },
            onEachFeature: (feature, layer) => {
                const stateName = feature.properties.ST_NM;
                const normName = self.normalizeStateName(stateName);
                const entry = self.regionsData.find(e => self.normalizeStateName(e.region) === normName);
                const count = entry ? entry.total : 0;
                const severity = entry ? entry.severity : 'none';
                const color = self.getSeverityColor(severity);

                // Bind custom tooltip
                layer.bindTooltip(`
                    <div style="font-family: 'Inter', sans-serif; font-size:12px; line-height:1.4;">
                        <strong style="font-size:13px;color:#1e293b;">${stateName}</strong><br/>
                        <span style="font-weight:700;color:${color}">${severity.toUpperCase()} RISK</span><br/>
                        <span style="color:#64748b;">${count} Reported Alerts</span>
                    </div>
                `, {
                    direction: 'auto',
                    sticky: true,
                    opacity: 0.95
                });

                // Attach mouse event overrides
                layer.on({
                    mouseover: (e) => {
                        const l = e.target;
                        const isSelected = self.selectedRegionName && self.normalizeStateName(self.selectedRegionName) === normName;
                        l.setStyle({
                            weight: isSelected ? 4.0 : 2.5,
                            color: isSelected ? '#1E3A8A' : '#475569',
                            fillOpacity: isSelected ? 0.8 : (entry ? 0.7 : 0.4)
                        });
                        if (!L.Browser.ie && !L.Browser.opera && !L.Browser.edge) {
                            l.bringToFront();
                        }
                    },
                    mouseout: (e) => {
                        const l = e.target;
                        const isSelected = self.selectedRegionName && self.normalizeStateName(self.selectedRegionName) === normName;
                        l.setStyle({
                            weight: isSelected ? 3.5 : 1.5,
                            color: isSelected ? '#1E3A8A' : '#CBD5E1',
                            fillOpacity: isSelected ? 0.75 : (entry ? 0.55 : 0.25)
                        });
                    },
                    click: () => {
                        self.selectRegionPolygon(stateName, layer);
                    }
                });
            }
        }).addTo(this.map);

        // Bind global handler for external triggers
        window.openState = (stateName) => {
            this.flyToRegion(stateName);
        };
    }

    updateData(heatmapEntries) {
        this.regionsData = heatmapEntries;
        console.log("Updating Heatmap Dataset. Layer redraw triggered:", heatmapEntries);
        this.renderGeoJson();
    }

    selectRegionPolygon(regionName, layer) {
        this.selectedRegionName = regionName;
        console.log("Region selected: " + regionName);

        // Redraw to apply selected border highlight styles
        this.renderGeoJson();

        // Smooth fly-to centering
        if (layer) {
            // Get center/bounds of the polygon layer
            const bounds = layer.getBounds();
            this.map.flyToBounds(bounds, {
                maxZoom: 6,
                animate: true,
                duration: 1.2
            });
        }

        if (this.onRegionSelect) {
            this.onRegionSelect(regionName);
        }
    }

    flyToRegion(regionName) {
        if (!this.geoJsonLayer) return;

        this.selectedRegionName = regionName;
        const normDest = this.normalizeStateName(regionName);

        // Locate layer corresponding to regionName
        let targetLayer = null;
        this.geoJsonLayer.eachLayer(l => {
            if (l.feature && l.feature.properties) {
                if (this.normalizeStateName(l.feature.properties.ST_NM) === normDest) {
                    targetLayer = l;
                }
            }
        });

        if (targetLayer) {
            this.selectRegionPolygon(regionName, targetLayer);
        } else {
            console.warn("Could not find matching GeoJSON polygon boundary for region: " + regionName);
            // Fallback: trigger callback even if no polygon matches
            if (this.onRegionSelect) {
                this.onRegionSelect(regionName);
            }
        }
    }

    resetSelection() {
        this.selectedRegionName = null;
        this.renderGeoJson();
        // Reset zoom center back to fit whole country
        this.map.setView([21.8467, 79.9462], 5, { animate: true });
    }

    // Retained for compatibility with filter callbacks in app.js
    filterMarkers(level) {
        if (!this.geoJsonLayer) return;
        const self = this;
        this.geoJsonLayer.eachLayer(l => {
            const stateName = l.feature.properties.ST_NM;
            const normName = self.normalizeStateName(stateName);
            const entry = self.regionsData.find(e => self.normalizeStateName(e.region) === normName);
            if (!entry) return;

            if (level === 'ALL' || entry.severity === level) {
                l.setStyle({ fillOpacity: 0.55, opacity: 1.0 });
            } else {
                l.setStyle({ fillOpacity: 0.05, opacity: 0.15 });
            }
        });
    }

    filterMarkersByType(type) {
        if (!this.geoJsonLayer) return;
        const self = this;
        this.geoJsonLayer.eachLayer(l => {
            const stateName = l.feature.properties.ST_NM;
            const normName = self.normalizeStateName(stateName);
            const entry = self.regionsData.find(e => self.normalizeStateName(e.region) === normName);
            if (!entry) return;

            if (type === 'ALL') {
                l.setStyle({ fillOpacity: 0.55, opacity: 1.0 });
                return;
            }

            let matches = false;
            if (type === 'fake_apk' && ['Maharashtra', 'Uttar Pradesh', 'Bihar', 'Delhi'].includes(entry.region)) matches = true;
            if (type === 'bank_alert' && ['Rajasthan', 'West Bengal', 'Gujarat'].includes(entry.region)) matches = true;
            if (type === 'vishing' && ['Karnataka', 'Tamil Nadu', 'Telangana'].includes(entry.region)) matches = true;
            if (type === 'otp_theft' && ['Maharashtra', 'Delhi', 'Haryana'].includes(entry.region)) matches = true;

            if (matches) {
                l.setStyle({ fillOpacity: 0.55, opacity: 1.0 });
            } else {
                l.setStyle({ fillOpacity: 0.05, opacity: 0.15 });
            }
        });
    }
}
