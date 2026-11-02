type Item = { title: string; detail: string; status: string; tone?: 'ok' | 'warning' };
type Props = { title: string; description: string; action: string; items: Item[] };
export function ModulePage({ title, description, action, items }: Props) {
  return <section><div className="page-heading"><div><p className="eyebrow">Gestión</p><h1>{title}</h1><p className="lead">{description}</p></div><button className="button">{action}</button></div><div className="toolbar" role="search"><label htmlFor={`search-${title}`}>Buscar</label><input id={`search-${title}`} placeholder={`Buscar en ${title.toLowerCase()}`} /><button className="button secondary">Filtrar</button></div><div className="list" aria-label={title}>{items.map(item => <article className="list-item" key={item.title}><div><h2>{item.title}</h2><p>{item.detail}</p></div><span className={`badge ${item.tone === 'warning' ? 'warning' : ''}`}>{item.tone === 'warning' ? '!' : '✓'} {item.status}</span></article>)}</div></section>;
}
