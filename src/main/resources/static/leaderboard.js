// Store the original data for resetting
let originalData = [];

var vers = "PSP/MOH07";

// Fetch and display leaderboard data
async function fetchLeaderboard() {
    try {
        let allPlayers = [];
        let offset = 0;
        const limit = 100;
        let hasMorePlayers = true;

        while (hasMorePlayers) {
            const response = await fetch(`/api/leaderboard?vers=${vers}&offset=${offset}&limit=${limit}`);
            const data = await response.json();
            
            if (data.length === 0) {
                hasMorePlayers = false;
            } else {
                allPlayers = allPlayers.concat(data);
                offset += limit;
                
                // If we got less than the limit, we've reached the end
                if (data.length < limit) {
                    hasMorePlayers = false;
                }
            }
        }

        originalData = [...allPlayers]; // Store original data
        displayLeaderboard(allPlayers);

        detailedStatsModal();
    } catch (error) {
        console.error('Error fetching leaderboard:', error);
        document.querySelector('tbody').innerHTML = '<tr><td colspan="19">Error loading leaderboard data</td></tr>';
    }
}

// Calculate KPM and DPM
function calculateRates(kills, deaths, playTimeSeconds) {
    const playTimeMinutes = playTimeSeconds / 60;
    const kpm = (kills / playTimeMinutes).toFixed(2);
    const dpm = (deaths / playTimeMinutes).toFixed(2);
    return { kpm, dpm };
}

// Extract map name without game mode
function getMapName(mapWithMode) {
    return mapWithMode.split(':')[0].trim();
}

// Display leaderboard data in the table
function displayLeaderboard(data) {
    const tbody = document.querySelector('tbody');
    tbody.innerHTML = '';

    // Update table headers
    const theadRow = document.querySelector('thead tr');
    theadRow.innerHTML = `
        <th>Rank</th>
        <th>Username</th>
        <th>Score</th>
        <th>Kills</th>
        <th>Deaths</th>
        <th>K/D Ratio</th>
        <th>Play Time</th>
        <th>Wins</th>
        <th>Losses</th>
    `;

    // Re-add sorting functionality to headers
    addSorting();

    // Store the data globally for sorting
    window.leaderboardData = {};
    data.forEach(player => {
        window.leaderboardData[player.username] = player;
    });

    data.forEach(player => {
        const kdRatio = player.deaths === 0 ? player.kills : (player.kills / player.deaths).toFixed(2);
        const score = player.kills - player.deaths;
        const { kpm, dpm } = calculateRates(player.kills, player.deaths, player.playTime);
        const mapName = getMapName(player.mostPlayedMap);
        let rowHtml = `
            <td>${player.rank}</td>
            <td class="username">${player.username} <a href="javascript:;" data-user="${player.username}" class="show-details">Detailed Stats</a></td>
            <td>${score.toLocaleString()}</td>
            <td>${player.kills.toLocaleString()}</td>
            <td>${player.deaths.toLocaleString()}</td>
            <td>${kdRatio}</td>
            <td>${player.playTimeString}</td>
            <td>${player.wins.toLocaleString()}</td>
            <td>${player.losses.toLocaleString()}</td>
        `;
        
        const row = document.createElement('tr');
        row.setAttribute('username', player.username);
        row.innerHTML = rowHtml;

        tbody.appendChild(row);
    });
}

// Add sorting functionality
function addSorting() {
    const headers = document.querySelectorAll('th');
    headers.forEach((header, index) => {
        header.style.cursor = 'pointer';
        header.addEventListener('click', () => {
            sortTable(index);
        });
    });
}

// Sort table by column
function sortTable(columnIndex) {
    const tbody = document.querySelector('tbody');
    const rows = Array.from(tbody.querySelectorAll('tr'));
    const header = document.querySelectorAll('th')[columnIndex];
    const isAscending = header.classList.contains('asc');
    
    // Remove all sort classes
    document.querySelectorAll('th').forEach(th => {
        th.classList.remove('asc', 'desc');
    });
    
    // Add sort class to clicked header
    header.classList.add(isAscending ? 'desc' : 'asc');
    
    // Get the column name for special handling
    const columnName = header.textContent.trim();
    
    // Sort rows
    rows.sort((a, b) => {
        const aValue = a.cells[columnIndex].textContent;
        const bValue = b.cells[columnIndex].textContent;
        
        // Special handling for playtime column
        if (columnName === 'Play Time') {
            const aData = window.leaderboardData[a.cells[1].textContent]; // Get data by username
            const bData = window.leaderboardData[b.cells[1].textContent];
            return isAscending ? 
                bData.playTime - aData.playTime : 
                aData.playTime - bData.playTime;
        }
        
        // Handle numeric values
        if (!isNaN(aValue) && !isNaN(bValue)) {
            return isAscending ? 
                parseFloat(bValue) - parseFloat(aValue) : 
                parseFloat(aValue) - parseFloat(bValue);
        }
        
        // Handle string values
        return isAscending ? 
            bValue.localeCompare(aValue) : 
            aValue.localeCompare(bValue);
    });
    
    // Reorder rows
    rows.forEach(row => tbody.appendChild(row));
}

// Theme switching functionality
function initThemeToggle() {
    const themeToggle = document.getElementById('theme-toggle');
    const prefersDarkScheme = window.matchMedia('(prefers-color-scheme: dark)');
    
    // Set initial theme based on system preference or saved preference
    const savedTheme = localStorage.getItem('theme');
    if (savedTheme === 'dark' || (!savedTheme && prefersDarkScheme.matches)) {
        document.documentElement.setAttribute('data-theme', 'dark');
        themeToggle.checked = true;
    }
    
    // Handle theme toggle
    themeToggle.addEventListener('change', (e) => {
        if (e.target.checked) {
            document.documentElement.setAttribute('data-theme', 'dark');
            localStorage.setItem('theme', 'dark');
        } else {
            document.documentElement.removeAttribute('data-theme');
            localStorage.setItem('theme', 'light');
        }
    });
}

// Reset sorting to default (by rank)
function resetSorting() {
    // Remove all sort classes
    document.querySelectorAll('th').forEach(th => {
        th.classList.remove('asc', 'desc');
    });
    
    // Display original data with current optional stats state
    displayLeaderboard(originalData);
}

// Enable the detailed stats modal
function detailedStatsModal()
{
    let triggers = document.querySelectorAll('.show-details');
    let close = document.querySelector('.close-modal');

    if (triggers)
    {
        const mBody = document.getElementById('StatsModalBody');
        const mHeader = document.getElementById('StatsModalHeader');

        triggers.forEach((trigger) => 
        {
            trigger.addEventListener('click', function() 
            {
                let player = window.leaderboardData[trigger.getAttribute('data-user')];

                const kdRatio = player.deaths === 0 ? player.kills : (player.kills / player.deaths).toFixed(2);
                const wlRatio = player.wins === 0 ? player.wins : (player.wins / player.losses).toFixed(2);
                const score = player.kills - player.deaths;
                const { kpm, dpm } = calculateRates(player.kills, player.deaths, player.playTime);
                const mapName = getMapName(player.mostPlayedMap);
                let rowHtml = `
                    <tr>
                        <td>Rank</td>
                        <td>${player.rank}</td>
                    </tr>
                    <tr>
                        <td>Score</td>
                        <td>${score.toLocaleString()}</td>
                    </tr>
                    <tr>
                        <td>Kills</td>
                        <td>${player.kills.toLocaleString()}</td>
                    </tr>
                    <tr>
                        <td>Deaths</td>
                        <td>${player.deaths.toLocaleString()}</td>
                    </tr>
                    <tr>
                        <td>Kill/Death Ratio</td>
                        <td>${kdRatio}</td>
                    </tr>
                    <tr>
                        <td>Accuracy</td>
                        <td>${player.accuracy.toFixed(2)}%</td>
                    </tr>
                    <tr>
                        <td>Kills Per Minute (KPM)</td>
                        <td>${kpm}</td>
                    </tr>
                    <tr>
                        <td>Deaths Per Minute (DPM)</td>
                        <td>${dpm}</td>
                    </tr>
                    <tr>
                        <td>Total Headshots</td>
                        <td>${player.headshots.toLocaleString()}</td>
                    </tr>
                    <tr>
                        <td>Total Play Time</td>
                        <td>${player.playTimeString}</td>
                    </tr>
                    <tr>
                        <td>Total Wins</td>
                        <td>${player.wins.toLocaleString()}</td>
                    </tr>
                    <tr>
                        <td>Total Losses</td>
                        <td>${player.losses.toLocaleString()}</td>
                    </tr>
                    <tr>
                        <td>Total Games</td>
                        <td>${player.wins + player.losses}</td>
                    </tr>
                    <tr>
                        <td>Win/Loss Ratio</td>
                        <td>${wlRatio}</td>
                    </tr>
                    <tr>
                        <td>Favorite Map</td>
                        <td>${mapName}</td>
                    </tr>
                    <tr>
                        <td>Favorite Team</td>
                        <td>${player.favoriteTeam}</td>
                    </tr>
                    <tr>
                        <td>Games Played: Deathmatch</td>
                        <td>${player.dmGames.toLocaleString()}</td>
                    </tr>
                    <tr>
                        <td>Games Played: Team Deathmatch</td>
                        <td>${player.tdmGames.toLocaleString()}</td>
                    </tr>
                    <tr>
                        <td>Games Played: Domination</td>
                        <td>${player.domGames.toLocaleString()}</td>
                    </tr>
                    <tr>
                        <td>Games Played: Demolition</td>
                        <td>${player.demGames.toLocaleString()}</td>
                    </tr>
                    <tr>
                        <td>Games Played: Hold the Line</td>
                        <td>${player.htlGames.toLocaleString()}</td>
                    </tr>
                    <tr>
                        <td>Games Played: Battle Lines</td>
                        <td>${player.blGames.toLocaleString()}</td>
                    </tr>
                    <tr>
                        <td>Games Played: Infiltration</td>
                        <td>${player.infGames.toLocaleString()}</td>
                    </tr>
                `;
        
                mHeader.innerHTML = 'Player: ' + player.username;
                mBody.innerHTML = rowHtml;

                document.body.classList.add('show-modal');
            });
        });
    }

    if (close)
    {
        close.addEventListener('click', function()
        {
            document.body.classList.remove('show-modal');
        });
    }
}

// Search functionality
function searchUsers()
{
    const searchBar = document.getElementById('UserSearch');
    let searchDebounce = null;

    searchBar.addEventListener('keyup', function() 
    {
        let searchTerm = searchBar.value ?? '';
        let rows;

        if (searchDebounce)
        {
            clearTimeout(searchDebounce);
        }

        if (searchTerm.length <= 0)
        {
            rows = document.querySelectorAll('.leaderboard-table .table-wrapper tbody tr.filtered-out');

            if (rows)
            {
                rows.forEach(row => 
                {
                    row.classList.remove('filtered-out');
                });
            }

            return;
        }

        searchDebounce = setTimeout(() => 
        {
            rows = document.querySelectorAll('.leaderboard-table .table-wrapper tbody tr');

            if (rows)
            {
                rows.forEach(row => 
                {
                    if (row.getAttribute('username').toLowerCase().search(searchTerm.toLowerCase()) <= -1)
                    {
                        row.classList.add('filtered-out');
                    } else 
                    {
                        row.classList.remove('filtered-out');
                    }
                });
            }
        }, 500);
    });
}

// Initialize the leaderboard
document.addEventListener('DOMContentLoaded', () => {
    fetchLeaderboard();

    addSorting();
    initThemeToggle();
    initVersionSelect();
    searchUsers();

    // Add reset button handler
    document.getElementById('reset-sort').addEventListener('click', resetSorting);
});

// Initialize version select
function initVersionSelect() {
    const versionSelect = document.getElementById('version-select');
    versionSelect.value = vers; // Set initial value
    
    versionSelect.addEventListener('change', (e) => {
        vers = e.target.value;
        fetchLeaderboard(); // Reload data with new version
    });
}

