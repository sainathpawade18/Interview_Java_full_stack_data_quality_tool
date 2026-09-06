import { useEffect, useState } from 'react'
import api from './api'

const empty = {recordCode:'',name:'',email:'',phone:'',category:'',statusValue:'ACTIVE'}

function Login({onLogin}) {
  const [form,setForm]=useState({username:'admin',password:'Admin@123'})
  const [error,setError]=useState('')
  const submit=async e=>{
    e.preventDefault(); setError('')
    try {
      const {data}=await api.post('/auth/login',form)
      localStorage.setItem('token',data.token); localStorage.setItem('role',data.role)
      localStorage.setItem('username',data.username); onLogin(data)
    } catch { setError('Invalid username or password') }
  }
  return <div className="login"><form onSubmit={submit} className="card">
    <h1>Data Quality Platform</h1><p>Master Data Governance</p>
    <input placeholder="Username" value={form.username} onChange={e=>setForm({...form,username:e.target.value})}/>
    <input placeholder="Password" type="password" value={form.password} onChange={e=>setForm({...form,password:e.target.value})}/>
    {error && <div className="error">{error}</div>}<button>Login</button>
    <small>Demo: admin / Admin@123</small>
  </form></div>
}

function App() {
  const [logged,setLogged]=useState(!!localStorage.getItem('token'))
  const [records,setRecords]=useState({content:[],totalPages:0})
  const [q,setQ]=useState('')
  const [form,setForm]=useState(empty)
  const [editing,setEditing]=useState(null)
  const [message,setMessage]=useState('')
  const role=localStorage.getItem('role')
  const username=localStorage.getItem('username')

  const load=async()=>{ const {data}=await api.get('/records',{params:{q,size:20,page:0}}); setRecords(data) }
  useEffect(()=>{if(logged)load()},[logged,q])

  if(!logged) return <Login onLogin={()=>setLogged(true)}/>

  const save=async e=>{
    e.preventDefault(); setMessage('')
    try {
      if(editing) await api.put(`/records/${editing}`,form)
      else await api.post('/records',form)
      setForm(empty); setEditing(null); setMessage('Saved successfully'); load()
    } catch(e){setMessage(e.response?.data?.message || 'Save failed')}
  }
  const upload=async e=>{
    const file=e.target.files[0]; if(!file)return
    const fd=new FormData(); fd.append('file',file)
    try { const {data}=await api.post('/records/upload',fd); setMessage(`Uploaded ${data.uploaded} records`); load() }
    catch(e){setMessage(e.response?.data?.message || 'Upload failed')}
    e.target.value=''
  }
  const logout=()=>{localStorage.clear();setLogged(false)}
  const edit=r=>{setEditing(r.id);setForm({recordCode:r.recordCode,name:r.name,email:r.email||'',phone:r.phone||'',category:r.category,statusValue:r.statusValue})}

  return <div>
    <header><div><b>DQ Platform</b><span> | Logged in: {username} ({role})</span></div><button onClick={logout}>Logout</button></header>
    <main>
      <section className="toolbar">
        <input placeholder="Search code or name..." value={q} onChange={e=>setQ(e.target.value)}/>
        {(role==='ADMIN'||role==='DATA_ENTRY') && <label className="button">Upload CSV/XLSX<input type="file" accept=".csv,.xlsx" onChange={upload} hidden/></label>}
      </section>

      {(role==='ADMIN'||role==='DATA_ENTRY') && <form onSubmit={save} className="card form">
        <h2>{editing?'Edit':'Add'} Master Record</h2>
        <div className="grid">
          {Object.keys(form).map(k=><input key={k} required={['recordCode','name','category','statusValue'].includes(k)}
            placeholder={k} value={form[k]} onChange={e=>setForm({...form,[k]:e.target.value})}/>)}
        </div>
        <button>{editing?'Update':'Create'}</button> {editing&&<button type="button" onClick={()=>{setEditing(null);setForm(empty)}}>Cancel</button>}
      </form>}

      {message&&<div className="message">{message}</div>}

      <div className="card">
        <h2>Master Data ({records.totalElements||0})</h2>
        <table><thead><tr><th>Code</th><th>Name</th><th>Email</th><th>Category</th><th>Status</th><th>Validation</th><th>Actions</th></tr></thead>
        <tbody>{records.content.map(r=><tr key={r.id}>
          <td>{r.recordCode}</td><td>{r.name}</td><td>{r.email}</td><td>{r.category}</td><td>{r.status}</td>
          <td className={r.validationErrors?'bad':'ok'}>{r.validationErrors||'Valid'}</td>
          <td className="actions">
            {(role==='ADMIN'||role==='DATA_ENTRY')&&<><button onClick={()=>edit(r)}>Edit</button>
            <button onClick={async()=>{await api.post(`/records/${r.id}/submit`);load()}}>Submit</button></>}
            {(role==='ADMIN'||role==='APPROVER')&&r.status==='PENDING_APPROVAL'&&<><button onClick={async()=>{await api.post(`/records/${r.id}/approve`);load()}}>Approve</button>
            <button onClick={async()=>{await api.post(`/records/${r.id}/reject`);load()}}>Reject</button></>}
          </td>
        </tr>)}</tbody></table>
      </div>
    </main>
  </div>
}
export default App
