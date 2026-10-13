import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {randomBytes} from 'node:crypto';
import {createRequire} from 'node:module';
import {spawnSync} from 'node:child_process';

const KIND = 'stockflow';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const project = KIND === 'business' ? 'phoenix-business-portfolio-demo' : 'stockflow-portfolio-demo';
const frontendPort = KIND === 'business' ? 5175 : 5185;
const backendPort = KIND === 'business' ? 18081 : 18085;
const base = `http://127.0.0.1:${backendPort}`;
const folder = path.join(root,'.demo');
const credentialsPath = path.join(folder,'credentials.json');
const run = (command,args) => {
  const result=spawnSync(command,args,{cwd:root,stdio:'inherit',shell:process.platform==='win32' && command==='npm.cmd'});
  if(result.error)throw result.error;
  if(result.status!==0)throw new Error(`${command} failed (${result.status})`);
};
const composeArgs=['compose','--env-file',path.join(folder,'env'),'-f','compose.demo.yml','-p',project];
const action=process.argv[2]||'start';
if(action==='stop'){run('docker',[...composeArgs,'stop']);process.exit(0);}
if(action!=='start')throw new Error('Use start or stop. Demo volumes are never deleted by this tool.');
await fs.mkdir(folder,{recursive:true});
let credentials;
try {credentials=JSON.parse(await fs.readFile(credentialsPath,'utf8'));}
catch(error){
  if(error.code!=='ENOENT')throw error;
  credentials={username:KIND==='business'?'demo.owner':'demo.admin',password:'Demo1'+randomBytes(18).toString('hex'),bootstrapPassword:'Bootstrap1'+randomBytes(18).toString('hex')};
  let hash='';
  if(KIND==='stockflow'){
    run(process.platform==='win32'?'npm.cmd':'npm',['ci','--prefix','stockflow-frontend']);
    const require=createRequire(path.join(root,'stockflow-frontend/package.json'));
    hash=require('bcryptjs').hashSync(credentials.password,12);
  }
  const values={DB_PASSWORD:randomBytes(24).toString('hex'),MYSQL_ROOT_PASSWORD:randomBytes(24).toString('hex'),JWT_SECRET:randomBytes(32).toString('base64'),BOOTSTRAP_OWNER_USERNAME:credentials.username,BOOTSTRAP_OWNER_PASSWORD:credentials.bootstrapPassword,BOOTSTRAP_ADMIN_USERNAME:credentials.username,BOOTSTRAP_ADMIN_PASSWORD_HASH:hash};
  await fs.writeFile(path.join(folder,'env'),Object.entries(values).map(([k,v])=>`${k}='${v}'`).join('\n')+'\n',{mode:0o600});
  await fs.writeFile(credentialsPath,JSON.stringify(credentials,null,2)+'\n',{mode:0o600});
}
run('docker',[...composeArgs,'up','-d','--build','--wait','--wait-timeout','300']);
let token;
async function request(endpoint,method='GET',body){
  const response=await fetch(base+'/api'+endpoint,{method,signal:AbortSignal.timeout(30000),headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:body?JSON.stringify(body):undefined});
  if(!response.ok)throw new Error(`${method} ${endpoint}: HTTP ${response.status}`);
  const text=await response.text();return text?JSON.parse(text):null;
}
let session;
for(let i=0;i<90;i++){
  try {
    const health=await fetch(base+(KIND==='business'?'/actuator/health':'/api/health/ready'),{signal:AbortSignal.timeout(5000)});
    if(health.ok)break;
  }catch{}
  if(i===89)throw new Error('Demo backend did not become ready.');
  await new Promise(resolve=>setTimeout(resolve,2000));
}
try {session=await request('/auth/login','POST',{username:credentials.username,password:credentials.password});}
catch(error){
  if(KIND!=='business'||!error.message.endsWith('HTTP 401'))throw error;
  session=await request('/auth/login','POST',{username:credentials.username,password:credentials.bootstrapPassword});
  token=session.accessToken;
  await request('/auth/change-password','POST',{currentPassword:credentials.bootstrapPassword,newPassword:credentials.password});
  session=await request('/auth/login','POST',{username:credentials.username,password:credentials.password});
}
token=session.accessToken||session.token;
const today=new Date().toISOString().slice(0,10);
const dateAfter=days=>new Date(Date.now()+days*86400000).toISOString().slice(0,10);
if(KIND==='business'){
  const members=await request('/members');
  if(members.totalElements===0){
    const settings=await request('/settings');
    await request('/settings','PUT',{...settings,gymName:'Phoenix Gym Business',phone:null,email:'demo@example.test',address:'Synthetic portfolio demonstration',logoUrl:null,currency:'INR',timeZone:'Asia/Kolkata',receiptFooter:'Demo receipt — synthetic data only',taxIdentifier:null,reminderDays:7,allowMultipleDailyCheckins:false});
    const plans=await request('/plans');
    for(const p of plans)await request('/plans/'+p.id,'PUT',{name:p.name,durationMonths:p.durationMonths,price:p.durationMonths*1500,description:'Demo membership plan',active:true});
    const names=['Aarav Demo','Meera Demo','Kavin Demo','Nila Demo','Arjun Demo','Divya Demo','Rohan Demo','Priya Demo'];
    for(let i=0;i<names.length;i++){
      const member=await request('/members','POST',{memberCode:`DEMO-${String(i+1).padStart(3,'0')}`,name:names[i],email:`member${i+1}@example.test`,phone:null,joinDate:dateAfter(-35-i),planId:plans[i%plans.length].id,membershipStartDate:dateAfter(-30),expiryDate:i===6?dateAfter(-3):dateAfter(i===7?4:20+i),status:i===6?'EXPIRED':'ACTIVE',notes:'Synthetic portfolio data'});
      if(i<5)await request('/payments/renew','POST',{memberId:member.id,planId:plans[i%plans.length].id,method:i%2?'UPI':'CASH',referenceNote:'DEMO transaction'});
      if(i<3){await request('/attendance/check-in/'+member.id,'POST');if(i===0)await request('/attendance/check-out/'+member.id,'POST');}
    }
    await request('/users','POST',{username:'demo.staff',displayName:'Demo Staff',role:'STAFF',password:'Staff1'+randomBytes(18).toString('hex'),active:true});
  }
}else{
  const products=await request('/products');
  if(products.totalElements===0){
    const category=await request('/categories','POST',{name:'Office essentials',description:'Synthetic catalog'});
    const supplier=await request('/suppliers','POST',{name:'Demo Supply Co.',contactPerson:'Demo supplier',email:'supplier@example.test',phone:null,address:'Synthetic demo address'});
    const customer=await request('/customers','POST',{name:'Demo Retail Ltd.',email:'customer@example.test',phone:null,address:'Synthetic demo address',active:true});
    const items=[];
    for(const [i,name] of ['Notebook pack','Desk organiser','Wireless mouse','USB-C cable'].entries()){
      const product=await request('/products','POST',{sku:`DEMO-${i+1}`,name,description:'Synthetic demo product',categoryId:category.id,supplierId:supplier.id,costPrice:[120,450,650,180][i],sellingPrice:[180,650,950,280][i],reorderLevel:10,unit:'piece',quantityInStock:0,active:true});
      items.push({productId:product.id,quantity:i===3?8:30,unitPrice:product.costPrice});
    }
    const purchase=await request('/purchase-orders','POST',{orderNumber:'PO-DEMO-001',supplierId:supplier.id,orderDate:today,notes:'Synthetic purchase',items});
    await request('/purchase-orders/'+purchase.id+'/status','PATCH',{status:'ORDERED'});
    await request('/purchase-orders/'+purchase.id+'/status','PATCH',{status:'RECEIVED'});
    const sale=await request('/sales-orders','POST',{orderNumber:'SO-DEMO-001',customerId:customer.id,orderDate:today,notes:'Synthetic sale',items:items.slice(0,3).map((item,i)=>({...item,quantity:3,unitPrice:[180,650,950][i]}))});
    await request('/sales-orders/'+sale.id+'/status','PATCH',{status:'CONFIRMED'});
    await request('/sales-orders/'+sale.id+'/status','PATCH',{status:'FULFILLED'});
  }
}
console.log(`Demo ready: http://localhost:${frontendPort}`);
console.log(`Local demo credentials: ${credentialsPath}`);
console.log('Only synthetic records in the separate demo database are used.');