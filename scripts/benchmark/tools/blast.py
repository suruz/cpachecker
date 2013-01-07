import subprocess

import benchmark.util as Util
import benchmark.tools.template

class Tool(benchmark.tools.template.BaseTool):

    def getExecutable(self):
        return Util.findExecutable('pblast.opt')


    def getVersion(self, executable):
        return subprocess.Popen([executable],
                                stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT).communicate()[0][6:9]


    def getName(self):
        return 'BLAST'


    def getStatus(self, returncode, returnsignal, output, isTimeout):
        status = "UNKNOWN"
        for line in output.splitlines():
            if line.startswith('Error found! The system is unsafe :-('):
                status = 'UNSAFE'
            elif line.startswith('No error found.  The system is safe :-)'):
                status = 'SAFE'
            elif (returncode == 2) and line.startswith('Fatal error: out of memory.'):
                status = 'OUT OF MEMORY'
            elif (returncode == 2) and line.startswith('Fatal error: exception Sys_error("Broken pipe")'):
                status = 'EXCEPTION'
            elif (returncode == 2) and line.startswith('Ack! The gremlins again!: Sys_error("Broken pipe")'):
                status = 'TIMEOUT'
        return status