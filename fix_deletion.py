import re

filepath = 'app/src/main/java/com/asyuhada/quran/MainActivity.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# The fuzzy replace deleted:
#                         }
#                     }
#                 )
#             }
#
#         }
#     }
# }
# 
# @Composable
# fun BookmarkSlotChip(
#     slotIndex: Int,

# and replaced it with nothing!

pattern = re.compile(r'Text\("Tutup"\)\s*surah: Int,')
replacement = r'''Text("Tutup")
                        }
                    }
                )
            }

        }
    }
}

@Composable
fun BookmarkSlotChip(
    slotIndex: Int,
    surah: Int,'''

content = pattern.sub(replacement, content)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
