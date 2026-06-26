const fs = require('fs');
const file = 'app/src/main/java/com/example/ui/screens/HabayebScreen.kt';
const lines = fs.readFileSync(file, 'utf-8').split('\n');
const newLines = lines.slice(0, 1185);

const targetIdx = newLines.findIndex(line => line.includes('val activeThemeColor'));
if (targetIdx !== -1) {
    newLines.splice(targetIdx, 0, 
        'import com.example.ui.screens.habayeb.components.AddCustomerPopup',
        'import com.example.ui.screens.habayeb.components.CustomerHistoryOverlay',
        'import com.example.ui.screens.habayeb.components.CustomerItemRow',
        'import com.example.ui.screens.habayeb.components.AddTransactionPopup',
        'import com.example.ui.screens.habayeb.components.CalculatorModal'
    );
}

fs.writeFileSync(file, newLines.join('\n'));
console.log('Truncated and added imports');
