const fs = require('fs');
const path = require('path');

function walkDir(dir, callback) {
  fs.readdirSync(dir).forEach(f => {
    let dirPath = path.join(dir, f);
    let isDirectory = fs.statSync(dirPath).isDirectory();
    isDirectory ? walkDir(dirPath, callback) : callback(path.join(dir, f));
  });
}

walkDir('app/src/main/java', function(filePath) {
  if (filePath.endsWith('.kt')) {
    let content = fs.readFileSync(filePath, 'utf8');
    let changed = false;
    
    // Replace specific imports
    const toReplace = ['AppSettings', 'FixedCommitment', 'TransactionDb', 'CustomCategory', 'DeletedItemEntity', 'HabayebCustomer', 'HabayebTransaction'];
    toReplace.forEach(ent => {
        let regex = new RegExp(`import com.example.data.local.${ent}\\b`, 'g');
        if (regex.test(content)) {
            content = content.replace(regex, `import com.example.data.local.entities.${ent}`);
            changed = true;
        }
    });

    // Replace wildcard imports
    if (content.includes('import com.example.data.local.*') && !content.includes('import com.example.data.local.entities.*')) {
        content = content.replace(/import com.example.data.local.\*/g, 'import com.example.data.local.*\nimport com.example.data.local.entities.*');
        changed = true;
    }

    if (changed) {
        fs.writeFileSync(filePath, content, 'utf8');
        console.log('Updated', filePath);
    }
  }
});
