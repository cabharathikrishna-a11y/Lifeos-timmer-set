package com.example.ui.components

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.AppViewModel
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.Charcoal
import com.example.ui.theme.MonospaceNumbers
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.WaterBlue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date
import com.example.api.AnalyticsVaultEngine

@Composable
fun AnalyticsView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val tasks by viewModel.tasks.collectAsState()
    val habits by viewModel.habits.collectAsState()
    val completions by viewModel.habitCompletions.collectAsState()
    val focusRecords by viewModel.focusRecords.collectAsState()
    val dailyFocusHoursTarget by viewModel.dailyFocusHoursTarget.collectAsState()
    val historyRecords by viewModel.allHistoryVault.collectAsState()

    var selectedTimelineDate by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    val dayRecords = remember(historyRecords, selectedTimelineDate) {
        historyRecords.filter { it.date_string == selectedTimelineDate }
    }

    // Lazy fallback fetch when day records are empty and device is online
    LaunchedEffect(selectedTimelineDate, dayRecords) {
        if (dayRecords.isEmpty()) {
            viewModel.lazyFetchAndCacheDailyLedger(selectedTimelineDate)
        }
    }

    val streakCount = remember(historyRecords) {
        AnalyticsVaultEngine.calculateDailyConsistencyStreak(context, historyRecords)
    }

    val totalWeeklyMetrics = remember(historyRecords) {
        val cal = Calendar.getInstance()
        val endStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val startStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        AnalyticsVaultEngine.calculateMetricsForWindow(historyRecords, startStr, endStr)
    }

    val totalMonthlyMetrics = remember(historyRecords) {
        val cal = Calendar.getInstance()
        val endStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -29)
        val startStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        AnalyticsVaultEngine.calculateMetricsForWindow(historyRecords, startStr, endStr)
    }

    var chartTabSelection by remember { mutableStateOf(0) } // 0 = Past Week, 1 = Past Month

    // Aggregate statistics
    val completedTasksNum = tasks.count { it.isCompleted }
    val totalTasksNum = tasks.size
    val completionPercentage = if (totalTasksNum > 0) (completedTasksNum * 100) / totalTasksNum else 100

    val focusHoursLogged = tasks.sumOf { it.actualMinutes }.toDouble() / 60.0

    // Compute live chart data based on selectedTab
    val daysCount = if (chartTabSelection == 0) 7 else 30

    val chartDataJson = remember(tasks, chartTabSelection) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val labelSdf = SimpleDateFormat(if (daysCount == 7) "EEE" else "d", Locale.getDefault())
        val fullDateSdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

        val list = mutableListOf<String>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(daysCount - 1))

        for (i in 0 until daysCount) {
            val dateStr = sdf.format(cal.time)
            val label = labelSdf.format(cal.time)
            val fullDateStr = fullDateSdf.format(cal.time)

            val totalForDay = tasks.count { it.dueDateString == dateStr }
            val completedForDay = tasks.count { it.dueDateString == dateStr && it.isCompleted }
            val completionRate = if (totalForDay > 0) {
                (completedForDay.toDouble() / totalForDay.toDouble()) * 100.0
            } else {
                0.0
            }

            val obj = """
                {
                    "date": "$dateStr",
                    "label": "$label",
                    "fullDate": "$fullDateStr",
                    "scheduled": $totalForDay,
                    "completed": $completedForDay,
                    "rate": $completionRate
                }
            """.trimIndent()
            list.add(obj)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        "[${list.joinToString(",")}]"
    }

    val allDaysEmpty = remember(tasks, chartTabSelection) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(daysCount - 1))
        var hasAny = false
        for (i in 0 until daysCount) {
            val dateStr = sdf.format(cal.time)
            if (tasks.any { it.dueDateString == dateStr }) {
                hasAny = true
                break
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        !hasAny
    }

    // Modern D3.js SVG graph template with dual Y-axis lines + bars
    val htmlData = remember(chartDataJson) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <script src="https://cdn.jsdelivr.net/npm/d3@7"></script>
          <style>
            body {
              background-color: transparent !important;
              color: #FFFFFF;
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
              margin: 0;
              padding: 0;
              overflow: hidden;
              user-select: none;
              -webkit-user-select: none;
            }
            .chart-container {
              width: 100vw;
              height: 100vh;
              display: flex;
              flex-direction: column;
              align-items: center;
              box-sizing: border-box;
              padding: 4px;
            }
            #chart {
              width: 100%;
              flex-grow: 1;
              position: relative;
            }
            svg {
              width: 100%;
              height: 100%;
              display: block;
            }
            .axis-label text {
              font-size: 8px;
              fill: #8E8E93;
              font-weight: 500;
            }
            .grid line {
              stroke: #2E2E30;
              stroke-opacity: 0.8;
              shape-rendering: crispEdges;
            }
            .grid path {
              stroke-width: 0;
            }
            .bar-scheduled {
              fill: #2C2C2E;
              rx: 2px;
            }
            .bar-completed {
              fill: #44D7B6; /* Custom green color matching telemetry */
              rx: 2px;
            }
            .line-rate {
              fill: none;
              stroke: #2E6FF3; /* Beautiful accent blue */
              stroke-width: 2.5;
              stroke-linecap: round;
            }
            .area-rate {
              fill: url(#rate-gradient);
              opacity: 0.12;
            }
            .dot-rate {
              fill: #2E6FF3;
              stroke: #151517;
              stroke-width: 1.5;
            }
            .tooltip {
              position: absolute;
              background: rgba(20, 20, 22, 0.96);
              border: 1px solid #2E2E30;
              border-radius: 6px;
              padding: 6px 10px;
              color: #FFF;
              font-size: 10px;
              pointer-events: none;
              box-shadow: 0 4px 10px rgba(0,0,0,0.5);
              font-family: monospace;
              display: none;
              z-index: 10;
            }
            .tooltip-title {
              color: #8E8E93;
              margin-bottom: 3px;
              font-weight: bold;
              font-size: 8px;
              text-transform: uppercase;
              letter-spacing: 0.5px;
            }
          </style>
        </head>
        <body>
          <div class="chart-container">
            <div id="chart"></div>
            <div id="tooltip" class="tooltip"></div>
          </div>

          <script>
            try {
              const data = $chartDataJson;

              // Generate sizes dynamically
              const chartDiv = document.getElementById("chart");
              const width = chartDiv.clientWidth || 360;
              const height = chartDiv.clientHeight || 180;
              const margin = {top: 10, right: 30, bottom: 20, left: 24};
              const chartWidth = width - margin.left - margin.right;
              const chartHeight = height - margin.top - margin.bottom;

              // Append primary svg
              const svg = d3.select("#chart")
                .append("svg")
                .attr("width", width)
                .attr("height", height)
                .append("g")
                .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

              // Define custom color gradient
              const defs = svg.append("defs");
              const rateGradient = defs.append("linearGradient")
                .attr("id", "rate-gradient")
                .attr("x1", "0%").attr("y1", "0%")
                .attr("x2", "0%").attr("y2", "100%");
              rateGradient.append("stop")
                .attr("offset", "0%")
                .attr("stop-color", "#2E6FF3")
                .attr("stop-opacity", 0.4);
              rateGradient.append("stop")
                .attr("offset", "100%")
                .attr("stop-color", "#2E6FF3")
                .attr("stop-opacity", 0);

              // X-Scale Configuration
              const x = d3.scaleBand()
                .domain(data.map(d => d.label))
                .range([0, chartWidth])
                .padding(0.4);

              // Left Y-Scale (Volume of scheduled and completed tasks)
              const maxTasks = d3.max(data, d => Math.max(d.scheduled, 1));
              const yLeft = d3.scaleLinear()
                .domain([0, maxTasks + 0.5])
                .range([chartHeight, 0]);

              // Right Y-Scale (Completion achievement %)
              const yRight = d3.scaleLinear()
                .domain([0, 100])
                .range([chartHeight, 0]);

              // Internal background grid line markers
              svg.append("g")
                .attr("class", "grid")
                .call(d3.axisLeft(yLeft)
                  .tickSize(-chartWidth)
                  .tickFormat("")
                  .ticks(Math.min(maxTasks + 1, 4))
                );

              // Display horizontal timeline (X ticks)
              svg.append("g")
                .attr("transform", "translate(0," + chartHeight + ")")
                .attr("class", "axis-label")
                .call(d3.axisBottom(x).tickSize(0).tickPadding(6))
                .call(g => g.select(".domain").remove());

              // Left Vertical Metric (Units)
              svg.append("g")
                .attr("class", "axis-label")
                .call(d3.axisLeft(yLeft).ticks(Math.min(maxTasks + 1, 4)).tickFormat(d3.format("d")).tickSize(0).tickPadding(4))
                .call(g => g.select(".domain").remove());

              // Right Vertical Achievement (%)
              svg.append("g")
                .attr("transform", "translate(" + chartWidth + ",0)")
                .attr("class", "axis-label")
                .call(d3.axisRight(yRight).ticks(4).tickFormat(d => d + "%").tickSize(0).tickPadding(4))
                .call(g => g.select(".domain").remove());

              // Bars rendering: Scheduled tasks (backbone bar layer)
              svg.selectAll(".bar-scheduled")
                .data(data)
                .enter()
                .append("rect")
                .attr("class", "bar-scheduled")
                .attr("x", d => x(d.label))
                .attr("y", d => yLeft(d.scheduled))
                .attr("width", x.bandwidth())
                .attr("height", d => Math.max(0, chartHeight - yLeft(d.scheduled)));

              // Bars rendering: Completed tasks (achievement indicator layer)
              svg.selectAll(".bar-completed")
                .data(data)
                .enter()
                .append("rect")
                .attr("class", "bar-completed")
                .attr("x", d => x(d.label))
                .attr("y", d => yLeft(d.completed))
                .attr("width", x.bandwidth())
                .attr("height", d => Math.max(0, chartHeight - yLeft(d.completed)));

              // Linear Area plotting: Completion trend shading
              const area = d3.area()
                .x(d => x(d.label) + x.bandwidth() / 2)
                .y0(chartHeight)
                .y1(d => yRight(d.rate))
                .curve(d3.curveMonotoneX);

              svg.append("path")
                .datum(data)
                .attr("class", "area-rate")
                .attr("d", area);

              // TrendLine plotting: High-contrast curve
              const line = d3.line()
                .x(d => x(d.label) + x.bandwidth() / 2)
                .y(d => yRight(d.rate))
                .curve(d3.curveMonotoneX);

              svg.append("path")
                .datum(data)
                .attr("class", "line-rate")
                .attr("d", line);

              // Circular nodes & sensory tooltips mapping
              svg.selectAll(".dot-rate")
                .data(data)
                .enter()
                .append("circle")
                .attr("class", "dot-rate")
                .attr("cx", d => x(d.label) + x.bandwidth() / 2)
                .attr("cy", d => yRight(d.rate))
                .attr("r", 3.5)
                .on("mouseover", function(event, d) {
                  d3.select(this).transition().duration(80).attr("r", 5.5);
                  const tooltip = d3.select("#tooltip");
                  tooltip.style("display", "block")
                    .html(
                      "<div class='tooltip-title'>" + d.fullDate + "</div>" +
                      "Scheduled: " + d.scheduled + "<br/>" +
                      "Completed: " + d.completed + "<br/>" +
                      "<span style='color:#2E6FF3;'>Efficiency: " + Math.round(d.rate) + "%</span>"
                    );
                })
                .on("mousemove", function(event) {
                  const tooltip = d3.select("#tooltip");
                  const outerDiv = document.getElementById("chart");
                  const rect = outerDiv.getBoundingClientRect();
                  
                  // Keep tooltip securely positioned near sensory touch indicator inside chart canvas
                  tooltip.style("left", (event.clientX - rect.left + 12) + "px")
                         .style("top", (event.clientY - rect.top - 45) + "px");
                })
                .on("mouseout", function() {
                  d3.select(this).transition().duration(80).attr("r", 3.5);
                  d3.select("#tooltip").style("display", "none");
                });
            } catch (err) {
              document.body.innerHTML = "<div style='color:red; font-size:10px; padding:10px;'>" + err.message + "</div>";
            }
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // 🚀 NEW ELEMENT: Visual Chronological Timeline Graph
        item {
            VisualTimelineGraph(
                dateString = selectedTimelineDate,
                dayRecords = dayRecords,
                onDateChange = { selectedTimelineDate = it }
            )
        }

        // 🔥 NEW ELEMENT: Daily Consistency Study Streak Meter
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (streakCount > 0) Color(0xFFFFB300).copy(alpha = 0.08f) else Color.White.copy(alpha = 0.05f)
                ),
                border = if (streakCount > 0) CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFFFB300))
                ) else null
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "STUDY CONSISTENCY STREAK",
                            fontSize = 10.sp,
                            color = if (streakCount > 0) Color(0xFFFFB300) else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (streakCount > 0) "$streakCount Consecutive Days! 🔥" else "No active streak yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            text = "Target: 6+ focus hours daily to sustain consistency index.",
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (streakCount > 0) Color(0xFFFFB300).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔥",
                            fontSize = 26.sp
                        )
                    }
                }
            }
        }

        // Metrics Grid Summary Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("FOCUS HOURS", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${String.format("%.1f", focusHoursLogged)}h",
                            style = MonospaceNumbers.copy(fontSize = 24.sp),
                            color = WaterBlue
                        )
                        Text("Logged overall", fontSize = 11.sp, color = Color.LightGray)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("TASKS METRIC", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text(
                            text = "$completionPercentage%",
                            style = MonospaceNumbers.copy(fontSize = 24.sp),
                            color = Color.Green
                        )
                        Text("$completedTasksNum done of $totalTasksNum", fontSize = 11.sp, color = Color.LightGray)
                    }
                }
            }
        }

        // 🧠 NEW ELEMENT: D3.js Task Completion Rate Performance Dashboard Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Custom interactive toggle matching application aesthetics
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Productivity Trends",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Past Week", "Past Month").forEachIndexed { index, label ->
                                val isSelected = chartTabSelection == index
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) WaterBlue.copy(alpha = 0.18f) else Color.Transparent)
                                        .clickable { chartTabSelection = index }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) WaterBlue else Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        // Embedded D3.js interactive chart WebView
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    settings.setSupportZoom(false)
                                    setBackgroundColor(0) // Safe transparent backdrop
                                    webViewClient = WebViewClient()
                                }
                            },
                            update = { webView ->
                                webView.loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null)
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Seeding Helper overlay if no task profiles scheduling is found
                        if (allDaysEmpty) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Charcoal.copy(alpha = 0.88f))
                                    .clickable(enabled = false, onClick = {}),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "No data telemetry available",
                                        tint = WaterBlue,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No scheduled tasks found for this period.",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Assign target due dates in 'Tasks Board' to visualize completion velocities and trends.",
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(top = 4.dp),
                                        lineHeight = 14.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Legend markers bar underneath the D3 charts container
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(1f)).background(Color(0xFF2C2C2E)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scheduled Tasks", fontSize = 10.sp, color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(1f)).background(Color(0xFF44D7B6)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Completed Tasks", fontSize = 10.sp, color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.width(12.dp).height(2.dp).background(Color(0xFF2E6FF3)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Completion Rate (%)", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }

        // Habit Consistency Progress bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Habit Streak Performance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (habits.isEmpty()) {
                        Text("No active habits initialized yet.", color = Color.Gray)
                    } else {
                        habits.forEach { habit ->
                            val currentComps = completions.count { it.habitId == habit.id }
                            val consistency = (minOf(10, currentComps) * 100) / 10
                            
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(habit.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Streak Index: ${habit.streakCount} Flames", fontSize = 11.sp, color = AccentOrange)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { consistency / 100f },
                                    color = WaterBlue,
                                    trackColor = SurfaceCard,
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                }
            }
        }

        // Weekly Focus Hours logged visual charts representation
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Weekly Focus Telemetry",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Target: $dailyFocusHoursTarget hrs/day",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = WaterBlue
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Draw minimalist bar graph using composed components (no canvas issues recursively)
                    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    
                    // Correct dynamic data calculation for the current week (Monday - Sunday)
                    val calendar = Calendar.getInstance()
                    val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    val diffToMonday = if (currentDayOfWeek == Calendar.SUNDAY) -6 else 2 - currentDayOfWeek

                    val startOfWeek = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, diffToMonday)
                    }
                    val weekDateStrings = (0..6).map { offset ->
                        val dayCal = Calendar.getInstance().apply {
                            time = startOfWeek.time
                            add(Calendar.DAY_OF_YEAR, offset)
                        }
                        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(dayCal.time)
                    }

                    val weeklyHours = weekDateStrings.map { dateStr ->
                        val recordMins = focusRecords.filter { it.dateString == dateStr }.sumOf { it.durationMinutes }
                        recordMins.toFloat() / 60f
                    }

                    val maxHeight = 100f
                    val currentDayIdx = if (currentDayOfWeek == Calendar.SUNDAY) 6 else currentDayOfWeek - 2

                    Row(
                        modifier = Modifier.fillMaxWidth().height(130.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        weeklyHours.forEachIndexed { idx, hrs ->
                            val divisor = if (dailyFocusHoursTarget > 0) dailyFocusHoursTarget.toFloat() else 8f
                            val ratio = minOf(1.0f, hrs / divisor)
                            val barHeight = maxOf(6.dp, (maxHeight * ratio).dp)

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = String.format(Locale.US, "%.1fh", hrs),
                                    fontSize = 9.sp,
                                    color = if (idx == currentDayIdx) WaterBlue else Color.LightGray,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(18.dp)
                                        .height(barHeight)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(if (idx == currentDayIdx) WaterBlue else WaterBlue.copy(alpha = 0.4f))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = days[idx],
                                    fontSize = 10.sp,
                                    color = if (idx == currentDayIdx) WaterBlue else Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

