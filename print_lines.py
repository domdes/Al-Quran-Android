import sys

def print_lines(filepath, start, end):
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
        
    for i in range(start, min(end, len(lines))):
        print(f"Line {i+1}: {lines[i].rstrip()}")

print_lines('app/src/main/java/com/asyuhada/quran/MainActivity.kt', 2665, 2690)
