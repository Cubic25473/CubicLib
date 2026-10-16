"""Static dashboard preview only. No robot data or simulated connection endpoints."""
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from functools import partial
from pathlib import Path
import argparse
parser=argparse.ArgumentParser();parser.add_argument('--port',type=int,default=8087);args=parser.parse_args()
root=Path(__file__).resolve().parents[1]/'cubic-ftc/src/main/assets/cubiclib'
class PreviewHandler(SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Cache-Control', 'no-store')
        super().end_headers()
server=ThreadingHTTPServer(('127.0.0.1',args.port),partial(PreviewHandler,directory=str(root)))
print(f'CubicLib local preview: http://127.0.0.1:{args.port} — select Load demo',flush=True)
try: server.serve_forever()
except KeyboardInterrupt: server.server_close()
