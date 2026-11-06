import io, sys, glob, re
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
paths = glob.glob(r'D:\WorkSpace\inkpress\inkpress-server\src\main\java\com\inkpress\controller\*.java')
for p in paths:
    t = open(p, encoding='utf-8').read()
    cls = re.search(r'class (\w+)', t)
    maps = re.findall(r'@(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping)\(([^)]*)\)', t)
    print('==', cls.group(1) if cls else p)
    for kind, arg in maps:
        m = re.search(r'"([^"]*)"', arg)
        print('  ', kind.replace('Mapping', '').ljust(4), m.group(1) if m else '(class-level)')
