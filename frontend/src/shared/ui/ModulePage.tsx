import { useMemo, useState } from 'react';

type Item = { title: string; detail: string; status: string; tone?: 'ok' | 'warning' };
type Props = { title: string; description: string; action: string; items: Item[] };

export function ModulePage({ title, description, action, items }: Props) {
  const [query, setQuery] = useState('');
  const [showForm, setShowForm] = useState(false);
  const visible = useMemo(() => items.filter(item =>
    `${item.title} ${item.detail} ${item.status}`.toLowerCase().includes(query.toLowerCase())), [items, query]);

  return <section>
    <div className="page-heading">
      <div><p className="eyebrow">Gestión</p><h1>{title}</h1><p className="lead">{description}</p></div>
      <button className="button" onClick={() => setShowForm(value => !value)}>{showForm ? 'Cancelar' : action}</button>
    </div>
    {showForm && <form className="quick-form" onSubmit={event => { event.preventDefault(); setShowForm(false); }}>
      <h2>{action}</h2><label>Descripción<input required placeholder="Escriba la información necesaria" /></label>
      <div><button className="button" type="submit">Guardar</button><button className="button secondary" type="button" onClick={() => setShowForm(false)}>Volver</button></div>
    </form>}
    <div className="toolbar" role="search">
      <label htmlFor={`search-${title}`}>Buscar</label>
      <input id={`search-${title}`} value={query} onChange={event => setQuery(event.target.value)} placeholder={`Buscar en ${title.toLowerCase()}`} />
      <button className="button secondary" onClick={() => setQuery('')}>Limpiar</button>
    </div>
    <p aria-live="polite">{visible.length} resultado(s)</p>
    <div className="list" aria-label={title}>
      {visible.map(item => <article className="list-item" key={item.title}><div><h2>{item.title}</h2><p>{item.detail}</p></div><span className={`badge ${item.tone === 'warning' ? 'warning' : ''}`}>{item.tone === 'warning' ? '!' : '✓'} {item.status}</span></article>)}
      {visible.length === 0 && <div className="panel"><h2>Sin resultados</h2><p>Pruebe con otro término o limpie la búsqueda.</p></div>}
    </div>
  </section>;
}
