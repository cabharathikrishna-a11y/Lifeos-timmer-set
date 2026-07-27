/**
 * SynchronizationAudit.ts
 * 
 * A robust TypeScript utility to audit, log, and cross-reference real-time database (RTDB)
 * deltas with Cloud Firestore session records. It detects discrepancies between the hot real-time
 * path and the consolidated cold-storage path, identifying pending reconciliation tasks so the
 * UI can display a 'Sync Pending' state.
 */

export interface RTDBDelta {
    id: string;
    username: string;
    path: string;
    value: any;
    timestamp: number;
    processed: boolean;
}

export interface FirestoreSessionRecord {
    sessionId: string;
    username: string;
    startTimeMs: number;
    endTimeMs: number;
    durationMs: number;
    subject: string;
    lastUpdatedTs: number;
}

export interface ReconciliationTask {
    id: string;
    username: string;
    source: 'RTDB' | 'Firestore';
    type: 'MISSING_SESSION' | 'DURATION_DISCREPANCY' | 'TIMESTAMP_MISMATCH';
    description: string;
    rtdbDeltaId?: string;
    firestoreSessionId?: string;
    details: {
        rtdbValue?: any;
        firestoreValue?: any;
        deltaMs?: number;
    };
    createdTs: number;
    resolved: boolean;
}

export interface SyncState {
    isSyncPending: boolean;
    pendingTasksCount: number;
    pendingTasks: ReconciliationTask[];
    lastAuditTs: number;
}

export class SynchronizationAudit {
    private rtdbDeltas: Map<string, RTDBDelta[]> = new Map();
    private reconciliationTasks: Map<string, ReconciliationTask[]> = new Map();
    private auditLogs: string[] = [];

    constructor() {
        this.logEvent("SynchronizationAudit initialized.");
    }

    private logEvent(message: string): void {
        const timestamp = new Date().toISOString();
        const formatted = `[${timestamp}] [AuditEngine] ${message}`;
        this.auditLogs.push(formatted);
        if (this.auditLogs.length > 1000) {
            this.auditLogs = this.auditLogs.slice(-500);
        }
        console.log(formatted);
    }

    /**
     * Logs a new RTDB delta change.
     */
    public logRTDBDelta(username: string, path: string, value: any, timestamp: number = Date.now()): string {
        const deltaId = `delta_${username}_${timestamp}_${Math.random().toString(36).substr(2, 5)}`;
        const delta: RTDBDelta = {
            id: deltaId,
            username,
            path,
            value,
            timestamp,
            processed: false
        };

        if (!this.rtdbDeltas.has(username)) {
            this.rtdbDeltas.set(username, []);
        }
        const deltas = this.rtdbDeltas.get(username)!;
        deltas.push(delta);
        if (deltas.length > 500) {
            this.rtdbDeltas.set(username, deltas.slice(-250));
        }

        this.logEvent(`Logged RTDB delta for user ${username}: Path='${path}', DeltaId='${deltaId}'`);
        return deltaId;
    }

    /**
     * Prunes processed deltas and resolved/expired reconciliation tasks older than 36 hours
     * to prevent memory leaks in long-running environments.
     */
    private pruneExpiredData(): void {
        const now = Date.now();
        const ttlMs = 36 * 60 * 60 * 1000; // 36 hours TTL

        // Prune RTDB deltas
        for (const [username, deltas] of this.rtdbDeltas.entries()) {
            const freshDeltas = deltas.filter(delta => {
                const age = now - delta.timestamp;
                return !delta.processed || age < ttlMs;
            });
            if (freshDeltas.length === 0) {
                this.rtdbDeltas.delete(username);
            } else {
                this.rtdbDeltas.set(username, freshDeltas);
            }
        }

        // Prune reconciliation tasks
        for (const [username, tasks] of this.reconciliationTasks.entries()) {
            const freshTasks = tasks.filter(task => {
                const age = now - task.createdTs;
                return !task.resolved || age < ttlMs;
            });
            if (freshTasks.length === 0) {
                this.reconciliationTasks.delete(username);
            } else {
                this.reconciliationTasks.set(username, freshTasks);
            }
        }
        
        // Also prune older audit logs if they grow too large
        if (this.auditLogs.length > 1000) {
            this.auditLogs = this.auditLogs.slice(-500);
        }
    }

    /**
     * Cross-references all recorded RTDB deltas with the actual Firestore session records
     * to identify pending reconciliation tasks.
     */
    public crossReference(username: string, firestoreRecords: FirestoreSessionRecord[]): ReconciliationTask[] {
        this.pruneExpiredData();
        this.logEvent(`Starting audit cross-reference for user: ${username} with ${firestoreRecords.length} Firestore records.`);
        const deltas = this.rtdbDeltas.get(username) || [];
        const activeTasks: ReconciliationTask[] = [];

        // 1. Check for missing sessions in Firestore. 
        // If RTDB delta reports a focusing state or completed session, but Firestore has no record
        // within that time window.
        const focusSessionDeltas = deltas.filter(d => d.path.includes("status") && d.value === "FOCUSING");
        
        focusSessionDeltas.forEach(delta => {
            // Check if there is a matching Firestore session near this timestamp (within a 5-minute buffer)
            const bufferMs = 5 * 60 * 1000;
            const hasMatchingFirestore = firestoreRecords.some(record => 
                Math.abs(record.startTimeMs - delta.timestamp) < bufferMs
            );

            if (!hasMatchingFirestore && !delta.processed) {
                const taskId = `task_${username}_missing_${delta.id}`;
                const task: ReconciliationTask = {
                    id: taskId,
                    username,
                    source: 'RTDB',
                    type: 'MISSING_SESSION',
                    description: `Active focus session reported in RTDB at ${new Date(delta.timestamp).toLocaleTimeString()} is missing in Firestore.`,
                    rtdbDeltaId: delta.id,
                    details: {
                        rtdbValue: delta.value,
                        deltaMs: Date.now() - delta.timestamp
                    },
                    createdTs: Date.now(),
                    resolved: false
                };
                activeTasks.push(task);
            }
        });

        // 2. Check for cumulative duration discrepancies
        const todayStr = new Date().toISOString().split('T')[0];
        const rtdbTimeDelta = deltas.find(d => d.path.includes("todaySavedFocusMs") || d.path.includes("accumulatedTimeMs"));
        
        if (rtdbTimeDelta) {
            const rtdbTotalMs = Number(rtdbTimeDelta.value) || 0;
            
            // Calculate sum of durations from Firestore for today
            const firestoreTodayTotalMs = firestoreRecords
                .filter(record => new Date(record.startTimeMs).toISOString().split('T')[0] === todayStr)
                .reduce((sum, record) => sum + record.durationMs, 0);

            const differenceMs = Math.abs(rtdbTotalMs - firestoreTodayTotalMs);
            const toleranceMs = 15 * 1000; // 15 seconds threshold

            if (differenceMs > toleranceMs) {
                const taskId = `task_${username}_discrepancy_${rtdbTimeDelta.id}`;
                const task: ReconciliationTask = {
                    id: taskId,
                    username,
                    source: 'RTDB',
                    type: 'DURATION_DISCREPANCY',
                    description: `Cumulative daily focus duration mismatch. RTDB: ${(rtdbTotalMs / 1000).toFixed(1)}s, Firestore: ${(firestoreTodayTotalMs / 1000).toFixed(1)}s (Diff: ${(differenceMs / 1000).toFixed(1)}s).`,
                    rtdbDeltaId: rtdbTimeDelta.id,
                    details: {
                        rtdbValue: rtdbTotalMs,
                        firestoreValue: firestoreTodayTotalMs,
                        deltaMs: differenceMs
                    },
                    createdTs: Date.now(),
                    resolved: false
                };
                activeTasks.push(task);
            }
        }

        // Store the reconciled results
        this.reconciliationTasks.set(username, activeTasks);
        this.logEvent(`Audit completed for ${username}. Identified ${activeTasks.length} pending reconciliation tasks.`);
        return activeTasks;
    }

    /**
     * Resolves a reconciliation task, optionally marking the corresponding delta as processed.
     */
    public resolveTask(username: string, taskId: string, markDeltaProcessed = true): void {
        const tasks = this.reconciliationTasks.get(username) || [];
        const task = tasks.find(t => t.id === taskId);
        
        if (task) {
            task.resolved = true;
            if (markDeltaProcessed && task.rtdbDeltaId) {
                const deltas = this.rtdbDeltas.get(username) || [];
                const delta = deltas.find(d => d.id === task.rtdbDeltaId);
                if (delta) {
                    delta.processed = true;
                    this.logEvent(`Marked RTDB delta ${delta.id} as processed.`);
                }
            }
            this.logEvent(`Resolved reconciliation task: ${taskId}`);
            
            // Remove resolved task from active list
            this.reconciliationTasks.set(username, tasks.filter(t => t.id !== taskId));
        }
    }

    /**
     * Retrieves the synchronization status for a specific user.
     * The UI reads this state to determine if 'Sync Pending' should be displayed.
     */
    public getSyncState(username: string): SyncState {
        const pendingTasks = this.reconciliationTasks.get(username) || [];
        const unresolved = pendingTasks.filter(t => !t.resolved);
        
        return {
            isSyncPending: unresolved.length > 0,
            pendingTasksCount: unresolved.length,
            pendingTasks: unresolved,
            lastAuditTs: Date.now()
        };
    }

    /**
     * Clear all logs and caches
     */
    public clearCache(username?: string): void {
        if (username) {
            this.rtdbDeltas.delete(username);
            this.reconciliationTasks.delete(username);
            this.logEvent(`Cleared audit cache for user: ${username}`);
        } else {
            this.rtdbDeltas.clear();
            this.reconciliationTasks.clear();
            this.logEvent("Cleared all user audit caches.");
        }
    }

    /**
     * Returns all internally logged audit trail strings
     */
    public getAuditTrail(): string[] {
        return this.auditLogs;
    }
}

// Export a singleton instance by default
export const syncAuditInstance = new SynchronizationAudit();
