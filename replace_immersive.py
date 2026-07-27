import sys

with open("app/src/main/java/com/example/ui/components/TimerView_Immersive.kt", "r") as f:
    lines = f.readlines()

new_lines = []
skip = False
for i, line in enumerate(lines):
    if i == 212:
        skip = True
        new_lines.append(line)
        new_lines.append("""            if (areControlsVisible) {
                val selectedTag by viewModel.attachedTag.collectAsStateWithLifecycle()
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    if (isFocusPhase) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TagInterlinkSearchVBar(
                                selectedTag = selectedTag,
                                onClear = { viewModel.attachTagToTimer("") },
                                onClick = { viewModel.setShowTagSelectionDialog(true) },
                                modifier = Modifier.weight(1f)
                            )
                            TaskInterlinkSearchVBar(
                                selectedTask = selectedTask,
                                onClear = { viewModel.attachTaskToTimer(null) },
                                onClick = { viewModel.setShowTaskSelectionDialog(true) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isFocusPhase) {
                            // Break Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .bouncyClick {
                                        if (isTabFocusTimerSelected) {
                                            viewModel.pauseTimer()
                                            viewModel.takeBreakFromPomodoro()
                                        } else {
                                            viewModel.pauseStopwatch()
                                            viewModel.takeBreakFromStopwatch()
                                        }
                                    }
                                    .glassmorphicCard(
                                        shape = RoundedCornerShape(12.dp),
                                        borderWidth = 0.5.dp,
                                        borderColor = Color(0x5581C784),
                                        backgroundColor = Color(0x334CAF50)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Break", color = Color(0xFF81C784), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            // Pause / Resume Button
                            val isActive = if (isTabFocusTimerSelected) isTimerActive else isStopwatchActive
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .bouncyClick {
                                        if (isTabFocusTimerSelected) {
                                            if (isActive) viewModel.pauseTimer() else viewModel.startTimer()
                                        } else {
                                            if (isActive) viewModel.pauseStopwatch() else viewModel.startStopwatch()
                                        }
                                    }
                                    .glassmorphicCard(
                                        shape = RoundedCornerShape(12.dp),
                                        borderWidth = 0.5.dp,
                                        borderColor = if (isActive) Color(0x33FFFFFF) else WaterBlue.copy(alpha = 0.5f),
                                        backgroundColor = if (isActive) Color(0x40222222) else WaterBlue.copy(alpha = 0.3f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (isActive) "Pause" else "Resume", color = if (isActive) Color.White else WaterBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            
                            // End Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .bouncyClick {
                                        if (isTabFocusTimerSelected) {
                                            viewModel.pauseTimer()
                                            viewModel.prepareAndShowEndSessionDialog("timer", cumulativeSessionFocusSeconds)
                                        } else {
                                            viewModel.pauseStopwatch()
                                            viewModel.prepareAndShowEndSessionDialog("stopwatch", stopwatchSeconds)
                                        }
                                    }
                                    .glassmorphicCard(
                                        shape = RoundedCornerShape(12.dp),
                                        borderWidth = 0.5.dp,
                                        borderColor = Color(0x15F9325D),
                                        backgroundColor = Color(0x40C62828)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("End", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        } else {
                            // BREAK PHASE
                            val isBreakActive = isTimerActive
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .bouncyClick {
                                        if (isBreakActive) {
                                            viewModel.pauseTimer()
                                        } else {
                                            viewModel.startTimer()
                                        }
                                    }
                                    .glassmorphicCard(
                                        shape = RoundedCornerShape(12.dp),
                                        borderWidth = 0.5.dp,
                                        borderColor = if (isBreakActive) Color(0x33FFFFFF) else WaterBlue.copy(alpha = 0.5f),
                                        backgroundColor = if (isBreakActive) Color(0x40222222) else WaterBlue.copy(alpha = 0.3f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (isBreakActive) "Pause" else "Resume", color = if (isBreakActive) Color.White else WaterBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            // Start Focus
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .bouncyClick {
                                        viewModel.pauseTimer()
                                        if (isTabFocusTimerSelected) {
                                            if (wasStartedFromStopwatch) {
                                                viewModel.switchToFocusPhaseFromStopwatch()
                                                viewModel.startStopwatch()
                                            } else {
                                                viewModel.resetWorkPhaseTimer(focusTimerDurationMins)
                                                viewModel.startTimer()
                                            }
                                        } else {
                                            viewModel.switchToFocusPhase()
                                            viewModel.startStopwatch()
                                        }
                                        viewModel.setTimerImmersive(true)
                                    }
                                    .glassmorphicCard(
                                        shape = RoundedCornerShape(12.dp),
                                        borderWidth = 0.5.dp,
                                        borderColor = WaterBlue.copy(alpha = 0.6f),
                                        backgroundColor = WaterBlue.copy(alpha = 0.35f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (isTabFocusTimerSelected && !wasStartedFromStopwatch) "Start Pomo" else "Start Stopw", color = WaterBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            // End Break
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .bouncyClick {
                                        if (isTabFocusTimerSelected) {
                                            viewModel.skipOrEndBreak()
                                        } else {
                                            viewModel.pauseTimer()
                                            viewModel.prepareAndShowEndSessionDialog("stopwatch", stopwatchSeconds)
                                        }
                                    }
                                    .glassmorphicCard(
                                        shape = RoundedCornerShape(12.dp),
                                        borderWidth = 0.5.dp,
                                        borderColor = Color(0x33C62828),
                                        backgroundColor = Color(0x22C62828)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("End", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
""")
        continue
    if i == 628:
        skip = False
        continue
    if not skip:
        new_lines.append(line)

with open("app/src/main/java/com/example/ui/components/TimerView_Immersive.kt", "w") as f:
    f.writelines(new_lines)

