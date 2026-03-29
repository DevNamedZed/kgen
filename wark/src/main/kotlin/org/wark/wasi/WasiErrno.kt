package org.wark.wasi

/**
 * WASI Preview 1 error codes and constants.
 */
object WasiErrno {
    const val SUCCESS = 0
    const val TOOBIG = 1
    const val ACCES = 2
    const val ADDRINUSE = 3
    const val ADDRNOTAVAIL = 4
    const val AFNOSUPPORT = 5
    const val AGAIN = 6
    const val ALREADY = 7
    const val BADF = 8
    const val BADMSG = 9
    const val BUSY = 10
    const val CANCELED = 11
    const val CHILD = 12
    const val CONNABORTED = 13
    const val CONNREFUSED = 14
    const val CONNRESET = 15
    const val DEADLK = 16
    const val DESTADDRREQ = 17
    const val DOM = 18
    const val DQUOT = 19
    const val EXIST = 20
    const val FAULT = 21
    const val FBIG = 22
    const val HOSTUNREACH = 23
    const val IDRM = 24
    const val ILSEQ = 25
    const val INPROGRESS = 26
    const val INTR = 27
    const val INVAL = 28
    const val IO = 29
    const val ISCONN = 30
    const val ISDIR = 31
    const val LOOP = 32
    const val MFILE = 33
    const val MLINK = 34
    const val MSGSIZE = 35
    const val MULTIHOP = 36
    const val NAMETOOLONG = 37
    const val NETDOWN = 38
    const val NETRESET = 39
    const val NETUNREACH = 40
    const val NFILE = 41
    const val NOBUFS = 42
    const val NODEV = 43
    const val NOENT = 44
    const val NOEXEC = 45
    const val NOLCK = 46
    const val NOLINK = 47
    const val NOMEM = 48
    const val NOMSG = 49
    const val NOPROTOOPT = 50
    const val NOSPC = 51
    const val NOSYS = 52
    const val NOTCONN = 53
    const val NOTDIR = 54
    const val NOTEMPTY = 55
    const val NOTRECOVERABLE = 56
    const val NOTSOCK = 57
    const val NOTSUP = 58
    const val NOTTY = 59
    const val NXIO = 60
    const val OVERFLOW = 61
    const val OWNERDEAD = 62
    const val PERM = 63
    const val PIPE = 64
    const val PROTO = 65
    const val PROTONOSUPPORT = 66
    const val PROTOTYPE = 67
    const val RANGE = 68
    const val ROFS = 69
    const val SPIPE = 70
    const val SRCH = 71
    const val STALE = 72
    const val TIMEDOUT = 73
    const val TXTBSY = 74
    const val XDEV = 75
    const val NOTCAPABLE = 76

    const val FILETYPE_UNKNOWN: Byte = 0
    const val FILETYPE_BLOCK_DEVICE: Byte = 1
    const val FILETYPE_CHARACTER_DEVICE: Byte = 2
    const val FILETYPE_DIRECTORY: Byte = 3
    const val FILETYPE_REGULAR_FILE: Byte = 4
    const val FILETYPE_SOCKET_DGRAM: Byte = 5
    const val FILETYPE_SOCKET_STREAM: Byte = 6
    const val FILETYPE_SYMBOLIC_LINK: Byte = 7

    const val CLOCK_REALTIME = 0
    const val CLOCK_MONOTONIC = 1
    const val CLOCK_PROCESS_CPUTIME = 2
    const val CLOCK_THREAD_CPUTIME = 3

    const val WHENCE_SET = 0
    const val WHENCE_CUR = 1
    const val WHENCE_END = 2

    const val OFLAG_CREAT = 1
    const val OFLAG_DIRECTORY = 2
    const val OFLAG_EXCL = 4
    const val OFLAG_TRUNC = 8

    const val RIGHTS_ALL = -1L

    const val PREOPENTYPE_DIR = 0
}
