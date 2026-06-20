import sys

def check_braces(filepath):
    depth = 0
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
        
    for i, line in enumerate(lines):
        for char in line:
            if char == '{':
                depth += 1
            elif char == '}':
                depth -= 1
    print(f"Final true depth: {depth}")

check_braces('app/src/main/java/com/asyuhada/quran/MainActivity.kt')
