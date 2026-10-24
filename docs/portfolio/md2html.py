"""docs/*.md -> 기술서와 같은 스타일의 인쇄용 HTML (작은 마크다운 부분집합만 지원)."""
import html, re, sys

src, out, footer, img_prefix = sys.argv[1:5]
CSS = open(sys.argv[5], encoding='utf-8').read().replace('__FOOTER__', footer)
GH = 'https://github.com/songjeongseon1/hooklens'

def slug(t):
    t = re.sub(r'<[^>]+>', '', t).lower()
    t = re.sub(r'[^\w\- ]', '', t)
    return t.replace(' ', '-')

def inline(t):
    t = re.sub(r'``\s?(.+?)\s?``', lambda m: keep_one(m.group(1)), t)
    t = re.sub(r'`([^`]+)`', lambda m: keep_one(m.group(1)), t)
    t = html.escape(t, quote=False).replace('\\|', '|')
    t = re.sub(r'!\[([^\]]*)\]\(([^)]+)\)', lambda m: f'<img src="{img_prefix}{m.group(2)}" alt="{m.group(1)}">', t)
    t = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', link, t)
    t = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', t)
    return re.sub('\x00(\\d+)\x00', lambda m: CODES[int(m.group(1))], t)

CODES = []
def keep_one(c):
    CODES.append('<code>' + html.escape(c).replace('\\|', '|') + '</code>')
    return f'\x00{len(CODES)-1}\x00'

def link(m):
    text, href = m.group(1), m.group(2)
    if href.endswith('.md'):  # 문서끼리 링크는 깃허브 주소로 (PDF에서도 열리게)
        href = f'{GH}/blob/main/docs/{href}'
    return f'<a href="{href}">{text}</a>'

def cells(row):
    row = row.strip().strip('|')
    return [c.strip() for c in re.split(r'(?<!\\)\|', row)]

lines = open(src, encoding='utf-8').read().split('\n')
body, i, first_h1 = [], 0, True
while i < len(lines):
    l = lines[i]
    if l.startswith('```'):
        j = i + 1
        while not lines[j].startswith('```'): j += 1
        body.append('<pre>' + html.escape('\n'.join(lines[i+1:j])) + '</pre>'); i = j + 1; continue
    m = re.match(r'(#{1,3}) (.*)', l)
    if m:
        lvl, text = len(m.group(1)), m.group(2)
        if lvl == 1:
            body.append(f'<h1>{inline(text)}</h1>')
        else:
            cls = ' class="pb"' if lvl == 2 and text.startswith('##PB') else ''
            body.append(f'<h{lvl} id="{slug(text)}"{cls}>{inline(text)}</h{lvl}>')
        i += 1; continue
    if l.startswith('|'):
        rows = []
        while i < len(lines) and lines[i].startswith('|'): rows.append(lines[i]); i += 1
        head = ''.join(f'<th>{inline(c)}</th>' for c in cells(rows[0]))
        trs = ''.join('<tr>' + ''.join(f'<td>{inline(c)}</td>' for c in cells(r)) + '</tr>' for r in rows[2:])
        body.append(f'<table><thead><tr>{head}</tr></thead><tbody>{trs}</tbody></table>'); continue
    if re.match(r'(- |\d+\. )', l):
        tag = 'ol' if l[0].isdigit() else 'ul'; items = []
        while i < len(lines) and re.match(r'(- |\d+\. )', lines[i]):
            items.append(re.sub(r'^(- |\d+\. )', '', lines[i])); i += 1
        body.append(f'<{tag}>' + ''.join(f'<li>{inline(x)}</li>' for x in items) + f'</{tag}>'); continue
    if l.startswith('> '):
        body.append(f'<blockquote>{inline(l[2:])}</blockquote>'); i += 1; continue
    if l.strip() == '---':
        i += 1; continue
    if l.strip():
        para = [l]; i += 1
        while i < len(lines) and lines[i].strip() and not re.match(r'(#|\||```|- |\d+\. |> )', lines[i]):
            para.append(lines[i]); i += 1
        p = inline(' '.join(para))
        body.append(f'<figure>{p}</figure>' if p.startswith('<img') else f'<p>{p}</p>'); continue
    i += 1

title = re.match(r'# (.*)', lines[0]).group(1)
# 제목 다음 첫 문단은 부제, 그 뒤에 깃허브 주소 상자
for k, b in enumerate(body):
    if b.startswith('<p>'):
        body[k] = b.replace('<p>', '<p class="lead">', 1) + \
            f'<div class="ghline"><span>GITHUB</span><a class="gh" href="{GH}">{GH}</a></div>'
        break
open(out, 'w', encoding='utf-8').write(f'''<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><title>{title}</title>
<link href="https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
<style>{CSS}</style></head><body>
{chr(10).join(body)}
</body></html>''')
print('ok', out)
