if (derivedClock !== 60) throw new Error('Second generation observation incomplete');
for (var row of derivedRows) {
    if (row.grandchildren.length < 2 * row.killed || row.grandchildren.length > 4 * row.killed) throw new Error('Invalid split count: ' + row.mode);
    for (var child of row.grandchildren) {
        if (child.size !== 1 || child.members !== 1 || !child.hostRegistered || child.facetRegistered) throw new Error('Second generation lost fusion identity: ' + row.mode);
    }
}
JSON.stringify(derivedRows.map(function(row) { return {mode: row.mode, killed: row.killed, grandchildren: row.grandchildren.length}; }));
