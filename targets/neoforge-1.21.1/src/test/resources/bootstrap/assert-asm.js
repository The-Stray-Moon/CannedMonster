if (!asmResult.done) throw new Error('ASM tick observation incomplete: ' + asmTick);
for (var check in asmResult.checks) if (!asmResult.checks[check]) throw new Error('ASM check failed: ' + check);
JSON.stringify(asmResult);
