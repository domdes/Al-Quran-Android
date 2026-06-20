import re

filepath = 'app/src/main/java/com/asyuhada/quran/MainActivity.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

pattern = re.compile(r'Text\("Tutup"\)\s*\}\s*\}\s*\)\s*\}\s*\}\s*\}\s*\}\s*@Composable\s*fun BookmarkSlotChip\(')
replacement = r'''Text("Tutup")
                        }
                    }
                )
            }

        }
    }

@Composable
fun BookmarkSlotChip('''

content = pattern.sub(replacement, content)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
