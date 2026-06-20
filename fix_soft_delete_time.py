import re

filepath = 'app/src/main/java/com/asyuhada/quran/MainActivity.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

pattern = re.compile(r'if \(b \!= null\) \{\s*val entity = b\.copy\(\s*surahNumber = 1,\s*ayahNumber = 1,\s*pageNumber = 1,\s*label = "\$\{parsed\.customName\} \|\| Kosong",\s*isDirty = true\s*\)')

replacement = r'''if (b != null) {
    val entity = b.copy(
        surahNumber = 1,
        ayahNumber = 1,
        pageNumber = 1,
        label = " || Kosong",
        updatedAt = System.currentTimeMillis(),
        isDirty = true
    )'''

def replacer(match):
    lines = match.group(0).split('\n')
    indent = len(lines[0]) - len(lines[0].lstrip())
    indent_str = ' ' * indent
    rep_lines = replacement.split('\n')
    return '\n'.join((indent_str if i > 0 else '') + line for i, line in enumerate(rep_lines))

content = pattern.sub(replacer, content)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
