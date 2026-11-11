# Client module registry: address map

Static pseudocode/string-table cross-reference only. Addresses are client RVAs; VA is `0x2a4416a0000 + RVA`. `native_*` labels are analysis names, not recovered source class names. UI labels are copied from TXT1; no behavior is inferred here.

The startup pseudocode calls registry builder `native_6a590` at RVA `0x6a590` from `analysis/pseudocode/client/003e.c:3542`. Builder body: `analysis/pseudocode/client/0006.c:11113`. Each row records its constructor call directly from that builder or through a group helper and a `native_62310(key)` lookup in the constructor body.

| Constructor / RVA | TXT1 UI label (index) | Description key → TXT1 index | Registry call (`source file:line; excerpt`) | Constructor key lookup (`source file:line; excerpt`) |
|---|---|---|---|---|
| `native_c40f0` / `0xc40f0` | `АирЗтуск` (#50066) | `0x1888caf782f5e221` → #5676 | `0006.c:11283` — `native_c40f0(local_res10)` | `000c.c:3611` — `native_62310(0x1888caf782f5e221)` |
| `native_c8750` / `0xc8750` | `НоШеб` (#52071) | `0x13dd832cd19108cf` → #4625 | `0006.c:11290` — `native_c8750(local_res10)` | `000c.c:8088` — `native_62310(0x13dd832cd19108cf)` |
| `native_b3130` / `0xb3130` | `НоФриендДамаге` (#37617) | `0x1d64a7bd962ee59b` → #6900 | `0006.c:13772` — `native_b3130(local_res8)` | `000b.c:3610` — `native_62310(0x1d64a7bd962ee59b)` |
| `native_b77f0` / `0xb77f0` | `ТапеМоузе` (#35989) | `0xd16e1457fbea82a4` → #49923 | `0006.c:13729` — `native_b77f0(local_res8)` | `000b.c:7326` — `native_62310(0xd16e1457fbea82a4)` |
| `native_bada0` / `0xbada0` | `ТриггерБот` (#26998) | `0xe8228a446ce33909` → #55134 | `0006.c:13541` — `native_bada0(local_res8)` | `000b.c:11005` — `native_62310(0xe8228a446ce33909)` |
| `native_145810` / `0x145810` | `ЗхошИнвизибле` (#30286) | `0x1a26f2aad1b4e2e2` → #6032 | `0006.c:14679` — `native_145810(local_res8)` | `0014.c:4824` — `native_62310(0x1a26f2aad1b4e2e2)` |
| `native_95b60` / `0x95b60` | `ЗтреамБйпазз` (#59998) | `0x33bc074ba432b87e` → #11755 | `0006.c:13226` — `native_95b60(local_res8)` | `0009.c:5151` — `native_62310(0x33bc074ba432b87e)` |
| `native_f1d20` / `0xf1d20` | no label matched | `0x1e2fc644a6b7c91c` → #7049 | `0006.c:11627` — `native_f1d20(local_res10)` | `000f.c:1716` — `native_62310(0x1e2fc644a6b7c91c)` |

Group helpers called by the builder: `native_6dc50 → native_95b60`, `native_6e2b0 → native_bada0`, `native_6e6f0 → native_b77f0`, `native_6e7f0 → native_b3130`, and `native_6fad0 → native_145810`.

The registry call expressions are assigned back through the decompiler's `local_res8` or `local_res10` temporaries; abbreviated call expressions are shown in the table. The TXT1 label/index and key values are in `analysis/text/text-strings.tsv`; constructor call sites and key-lookup excerpts are in `analysis/pseudocode/client/0006.c` and the source files cited above.

This map establishes static address/key associations only. It does not assert runtime activation, behavior, or original source-level class names.
