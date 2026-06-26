const fs = require('fs');
const file = './app/src/main/java/com/example/ui/screens/MainLedgerView.kt';
let content = fs.readFileSync(file, 'utf8');

const startMarker = '    // Unified Immutable Audit Log & Timeline';
const endMarker = '}' + String.fromCharCode(10) + String.fromCharCode(10) + '// Budget Advice message generator based on comparison';

const startIndex = content.indexOf(startMarker);
const endIndex = content.indexOf(endMarker);

if (startIndex !== -1 && endIndex !== -1) {
    const newContent = content.substring(0, startIndex) + endMarker;
    fs.writeFileSync(file, newContent, 'utf8');
    console.log('Successfully removed the Audit Logs Dialog block.');
} else {
    console.log('Markers not found. start:', startIndex, 'end:', endIndex);
}
