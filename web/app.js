const $ = (id) => document.getElementById(id);
const keyboard = $('keyboard');
const status = $('status');
const composer = $('composerText');
const before = $('beforeText');
const after = $('afterText');
let current = after.textContent;
let previous = current;
let recognition;
let listening = false;

for(let i=0;i<42;i++){const bar=document.createElement('i');$('waveform').appendChild(bar)}

const clean = (text) => {
  const fixes = {"ill":"I’ll","im":"I’m","dont":"don’t","cant":"can’t","tonite":"tonight","pls":"please","snd":"send","teh":"the","bfr":"before","mtng":"meeting"};
  let out=text.trim().split(/\s+/).map(word=>fixes[word.toLowerCase()]||word).join(' ');
  out=out.charAt(0).toUpperCase()+out.slice(1);
  return /[.!?]$/.test(out)?out:out+'.';
};

function applyTranscript(raw){
  previous=current; before.textContent=raw; current=clean(raw); after.textContent=current; composer.textContent=current;
  status.textContent='Ready to insert'; keyboard.classList.remove('listening'); listening=false;
}

function stop(){recognition?.stop();keyboard.classList.remove('listening');listening=false;status.textContent='Tap to speak'}

function start(){
  if(listening){stop();return} listening=true;keyboard.classList.add('listening');status.textContent='Listening…';
  const SpeechRecognition=window.SpeechRecognition||window.webkitSpeechRecognition;
  if(SpeechRecognition){recognition=new SpeechRecognition();recognition.lang='en-IN';recognition.interimResults=true;recognition.onresult=(e)=>{const raw=[...e.results].map(r=>r[0].transcript).join(' ');before.textContent=raw;after.textContent=clean(raw);if(e.results[e.results.length-1].isFinal)applyTranscript(raw)};recognition.onerror=()=>applyTranscript('Ill send the updated design tonite');recognition.onend=()=>{if(listening)stop()};recognition.start()}
  else setTimeout(()=>applyTranscript('Ill send the updated design tonite'),1800);
}

$('micButton').addEventListener('click',start);
$('undoButton').addEventListener('click',()=>{[current,previous]=[previous,current];after.textContent=current;composer.textContent=current;status.textContent='Undone'});
$('backspaceButton').addEventListener('click',()=>{previous=current;current='';before.textContent='—';after.textContent='—';composer.textContent='';status.textContent='Cleared'});
$('insertButton').addEventListener('click',async()=>{if(!current)return;try{await navigator.clipboard.writeText(current);status.textContent='Copied — paste it anywhere'}catch{status.textContent='Inserted into preview'}composer.textContent=current});
$('languageButton').addEventListener('click',()=>status.textContent='English (India) selected');
$('infoButton').addEventListener('click',()=>$('aboutDialog').showModal());
$('closeDialog').addEventListener('click',()=>$('aboutDialog').close());
if('serviceWorker' in navigator)navigator.serviceWorker.register('sw.js');
