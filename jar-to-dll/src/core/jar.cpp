#include "jar.h"
#include <algorithm>
#include <map>
#include <functional>
#include <cstring>


static constexpr uint32_t CLASS_MAGIC = 0xCAFEBABE;
static constexpr uint8_t CONSTANT_Utf8 = 1;
static constexpr uint8_t CONSTANT_Integer = 3;
static constexpr uint8_t CONSTANT_Float = 4;
static constexpr uint8_t CONSTANT_Long = 5;
static constexpr uint8_t CONSTANT_Double = 6;
static constexpr uint8_t CONSTANT_Class = 7;
static constexpr uint8_t CONSTANT_String = 8;
static constexpr uint8_t CONSTANT_FieldRef = 9;
static constexpr uint8_t CONSTANT_MethodRef = 10;
static constexpr uint8_t CONSTANT_InterfaceMethodRef = 11;
static constexpr uint8_t CONSTANT_NameAndType = 12;
static constexpr uint8_t CONSTANT_MethodHandle = 15;
static constexpr uint8_t CONSTANT_MethodType = 16;
static constexpr uint8_t CONSTANT_Dynamic = 17;
static constexpr uint8_t CONSTANT_InvokeDynamic = 18;
static constexpr uint8_t CONSTANT_Module = 19;
static constexpr uint8_t CONSTANT_Package = 20;


struct BitStream {
    const uint8_t* data;
    size_t size;
    size_t pos = 0;
    uint32_t bits = 0;
    int bitCount = 0;
    BitStream(const uint8_t* d, size_t s) : data(d), size(s) {}
    uint32_t read(int n) {
        while (bitCount < n) {
            if (pos >= size) return 0;
            bits |= static_cast<uint32_t>(data[pos++]) << bitCount;
            bitCount += 8;
        }
        uint32_t val = bits & ((1u << n) - 1);
        bits >>= n;
        bitCount -= n;
        return val;
    }
    void align() { bits = 0; bitCount = 0; }
};

struct HuffmanTable {
    int maxBits = 0;
    std::vector<int> counts;
    std::vector<int> symbols;
    void build(const int* lengths, int count) {
        maxBits = 0;
        for (int i = 0; i < count; i++)
            if (lengths[i] > maxBits) maxBits = lengths[i];
        if (maxBits == 0) return;
        counts.resize(static_cast<size_t>(maxBits) + 1, 0);
        for (int i = 0; i < count; i++)
            if (lengths[i] > 0) counts[static_cast<size_t>(lengths[i])]++;
        std::vector<int> nextCode(static_cast<size_t>(maxBits) + 1);
        nextCode[1] = 0;
        for (int i = 1; i < maxBits; i++)
            nextCode[static_cast<size_t>(i) + 1] = nextCode[static_cast<size_t>(i)] + counts[static_cast<size_t>(i)];
        symbols.resize(static_cast<size_t>(1) << maxBits, -1);
        std::vector<int> code(static_cast<size_t>(maxBits) + 1, 0);
        for (int i = 1; i <= maxBits; i++)
            code[static_cast<size_t>(i)] = (code[static_cast<size_t>(i) - 1] + counts[static_cast<size_t>(i) - 1]) << 1;
        for (int i = 0; i < count; i++) {
            int len = lengths[i];
            if (len > 0) {
                int c = code[static_cast<size_t>(len)]++;
                int rev = 0;
                for (int j = 0; j < len; j++) rev = (rev << 1) | ((c >> j) & 1);
                int step = 1 << len;
                for (int j = rev; j < (1 << maxBits); j += step)
                    symbols[static_cast<size_t>(j)] = i | (len << 16);
            }
        }
    }
    int decode(BitStream& bs) const {
        if (maxBits == 0) return -1;
        uint32_t bits = bs.bits;
        int bitCount = bs.bitCount;
        while (bitCount < maxBits) {
            if (bs.pos >= bs.size) break;
            bits |= static_cast<uint32_t>(bs.data[bs.pos++]) << bitCount;
            bitCount += 8;
        }
        int entry = symbols[bits & ((1u << maxBits) - 1)];
        if (entry < 0) return -1;
        int len = entry >> 16;
        int sym = entry & 0xFFFF;
        bs.bits = bits >> len;
        bs.bitCount = bitCount - len;
        return sym;
    }
};

static void buildFixedTables(HuffmanTable& litLen, HuffmanTable& dist) {
    int lengths[288];
    for (int i = 0; i < 144; i++) lengths[i] = 8;
    for (int i = 144; i < 256; i++) lengths[i] = 9;
    for (int i = 256; i < 280; i++) lengths[i] = 7;
    for (int i = 280; i < 288; i++) lengths[i] = 8;
    litLen.build(lengths, 288);
    int distLengths[32];
    for (int i = 0; i < 32; i++) distLengths[i] = 5;
    dist.build(distLengths, 32);
}

static std::vector<uint8_t> inflateData(const uint8_t* data, size_t compSize, size_t uncompSize) {
    std::vector<uint8_t> output;
    output.reserve(uncompSize);
    BitStream bs(data, compSize);

    static constexpr int lenBase[]  = {3,4,5,6,7,8,9,10,11,13,15,17,19,23,27,31,35,43,51,59,67,83,99,115,131,163,195,227,258};
    static constexpr int lenExtra[] = {0,0,0,0,0,0,0,0,1,1,1,1,2,2,2,2,3,3,3,3,4,4,4,4,5,5,5,5,0};
    static constexpr int distBase[] = {1,2,3,4,5,7,9,13,17,25,33,49,65,97,129,193,257,385,513,769,1025,1537,2049,3073,4097,6145,8193,12289,16385,24577};
    static constexpr int distExtra[]= {0,0,0,0,1,1,2,2,3,3,4,4,5,5,6,6,7,7,8,8,9,9,10,10,11,11,12,12,13,13};

    bool final = false;
    while (!final && bs.pos < bs.size) {
        final = bs.read(1) != 0;
        int type = static_cast<int>(bs.read(2));
        if (type == 0) {
            bs.align();
            if (bs.pos + 4 > bs.size) break;
            uint16_t len = static_cast<uint16_t>(bs.data[bs.pos] | (bs.data[bs.pos + 1] << 8));
            bs.pos += 4;
            if (bs.pos + len > bs.size) break;
            output.insert(output.end(), bs.data + bs.pos, bs.data + bs.pos + len);
            bs.pos += len;
        } else if (type == 1 || type == 2) {
            HuffmanTable litLen, dist;
            if (type == 1) {
                buildFixedTables(litLen, dist);
            } else {
                int hlit  = static_cast<int>(bs.read(5)) + 257;
                int hdist = static_cast<int>(bs.read(5)) + 1;
                int hclen = static_cast<int>(bs.read(4)) + 4;
                static constexpr int order[] = {16,17,18,0,8,7,9,6,10,5,11,4,12,3,13,2,14,1,15};
                int codeLengths[19] = {0};
                for (int i = 0; i < hclen; i++)
                    codeLengths[order[i]] = static_cast<int>(bs.read(3));
                HuffmanTable codeTable;
                codeTable.build(codeLengths, 19);
                std::vector<int> lengths(static_cast<size_t>(hlit + hdist));
                int i = 0;
                while (i < hlit + hdist) {
                    int sym = codeTable.decode(bs);
                    if (sym < 16) {
                        lengths[static_cast<size_t>(i++)] = sym;
                    } else if (sym == 16) {
                        int rep = static_cast<int>(bs.read(2)) + 3;
                        int val = i > 0 ? lengths[static_cast<size_t>(i) - 1] : 0;
                        while (rep-- > 0 && i < hlit + hdist)
                            lengths[static_cast<size_t>(i++)] = val;
                    } else if (sym == 17) {
                        int rep = static_cast<int>(bs.read(3)) + 3;
                        while (rep-- > 0 && i < hlit + hdist)
                            lengths[static_cast<size_t>(i++)] = 0;
                    } else if (sym == 18) {
                        int rep = static_cast<int>(bs.read(7)) + 11;
                        while (rep-- > 0 && i < hlit + hdist)
                            lengths[static_cast<size_t>(i++)] = 0;
                    }
                }
                litLen.build(lengths.data(), hlit);
                dist.build(lengths.data() + hlit, hdist);
            }
            while (true) {
                int sym = litLen.decode(bs);
                if (sym < 0 || sym == 256) break;
                if (sym < 256) {
                    output.push_back(static_cast<uint8_t>(sym));
                } else {
                    sym -= 257;
                    if (sym >= 29) break;
                    int len = lenBase[sym] + static_cast<int>(bs.read(lenExtra[sym]));
                    int distSym = dist.decode(bs);
                    if (distSym < 0 || distSym >= 30) break;
                    int distance = distBase[distSym] + static_cast<int>(bs.read(distExtra[distSym]));
                    size_t start = output.size() - static_cast<size_t>(distance);
                    for (int j = 0; j < len; j++)
                        output.push_back(output[start + static_cast<size_t>(j)]);
                }
            }
        } else break;
    }
    return output;
}


bool Jar::open(const std::string& path) {
    cached = false;
    cache.clear();
    files.clear();
    file.open(path, std::ios::binary);
    if (!file) return false;
    file.seekg(0, std::ios::end);
    auto size = static_cast<size_t>(file.tellg());
    if (size < 22) { file.close(); return false; }
    size_t searchStart = (size > 65557) ? size - 65557 : 0;
    file.seekg(static_cast<std::streamoff>(searchStart));
    std::vector<uint8_t> buf(size - searchStart);
    file.read(reinterpret_cast<char*>(buf.data()), static_cast<std::streamsize>(buf.size()));
    size_t endPos = std::string::npos;
    for (size_t i = buf.size() - 22; i != static_cast<size_t>(-1); i--) {
        if (buf[i] == 0x50 && buf[i+1] == 0x4B && buf[i+2] == 0x05 && buf[i+3] == 0x06) {
            endPos = searchStart + i;
            break;
        }
    }
    if (endPos == std::string::npos) { file.close(); return false; }
    file.seekg(static_cast<std::streamoff>(endPos));
    ZipEnd endRec;
    file.read(reinterpret_cast<char*>(&endRec), sizeof(endRec));
    file.seekg(endRec.centralOffset);
    for (uint16_t i = 0; i < endRec.totalEntries; i++) {
        ZipCentral central;
        file.read(reinterpret_cast<char*>(&central), sizeof(central));
        if (central.sig != 0x02014B50) break;
        std::string name(central.nameLen, '\0');
        file.read(name.data(), central.nameLen);
        file.seekg(central.extraLen + central.commentLen, std::ios::cur);
        ZipFile zf;
        zf.name      = name;
        zf.compSize  = central.compSize;
        zf.uncompSize= central.uncompSize;
        zf.method    = central.method;
        zf.offset    = central.offset;
        files.push_back(zf);
    }
    return true;
}

void Jar::close() {
    file.close();
    files.clear();
    cache.clear();
    cached = false;
}

std::vector<uint8_t> Jar::readEntry(const ZipFile& entry) {
    file.seekg(entry.offset);
    ZipLocal local;
    file.read(reinterpret_cast<char*>(&local), sizeof(local));
    file.seekg(local.nameLen + local.extraLen, std::ios::cur);
    std::vector<uint8_t> compressed(entry.compSize);
    file.read(reinterpret_cast<char*>(compressed.data()), static_cast<std::streamsize>(entry.compSize));
    if (entry.method == 0) return compressed;
    if (entry.method == 8) return inflate(compressed.data(), compressed.size(), entry.uncompSize);
    return {};
}

std::vector<uint8_t> Jar::readByName(const std::string& name) {
    for (auto& e : files)
        if (e.name == name) return readEntry(e);
    return {};
}

bool Jar::hasFile(const std::string& name) {
    for (auto& e : files)
        if (e.name == name) return true;
    return false;
}

std::vector<uint8_t> Jar::inflate(const uint8_t* data, size_t compSize, size_t uncompSize) {
    return inflateData(data, compSize, uncompSize);
}


bool Jar::shouldSkip(const std::string& name) {
    std::string lower = name;
    std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
    if (!lower.empty() && lower.back() == '/') return true;
    if (lower.find(".class") == std::string::npos) return true;
    if (lower.find("module-info.class") != std::string::npos) return true;
    if (lower.find("package-info.class") != std::string::npos) return true;
    return false;
}

void Jar::buildCache() {
    if (cached) return;
    for (auto& zf : files) {
        if (zf.name.back() == '/') continue;
        if (shouldSkip(zf.name)) continue;
        auto data = readEntry(zf);
        if (data.empty()) continue;
        JarEntry entry;
        entry.name = zf.name;
        entry.data = std::move(data);
        entry.isClass = entry.data.size() > 4 &&
                        entry.data[0] == 0xCA && entry.data[1] == 0xFE &&
                        entry.data[2] == 0xBA && entry.data[3] == 0xBE;
        entry.loadPriority = 0;
        entry.isModInitializer = false;
        entry.isClientModInitializer = false;

        if (entry.isClass) {
            std::string className = entry.name;
            if (className.size() > 6 && className.substr(className.size() - 6) == ".class")
                className = className.substr(0, className.size() - 6);
            std::replace(className.begin(), className.end(), '/', '.');
            entry.name = className;
            parseClassInfo(entry);
        }
        cache.push_back(std::move(entry));
    }
    cached = true;
}

std::vector<JarEntry> Jar::getClasses() {
    buildCache();
    sortClassesByDependency();
    std::vector<JarEntry> result;
    for (auto& e : cache)
        if (e.isClass) result.push_back(e);
    return result;
}


void Jar::parseClassInfo(JarEntry& entry) {
    if (!entry.isClass || entry.data.size() < 10) return;
    const uint8_t* data = entry.data.data();
    size_t size = entry.data.size();
    size_t pos = 0;
    auto readU2 = [&]() -> uint16_t {
        if (pos + 2 > size) return 0;
        uint16_t v = static_cast<uint16_t>((data[pos] << 8) | data[pos + 1]);
        pos += 2;
        return v;
    };
    auto readU4 = [&]() -> uint32_t {
        if (pos + 4 > size) return 0;
        uint32_t v = (static_cast<uint32_t>(data[pos]) << 24) |
                     (static_cast<uint32_t>(data[pos + 1]) << 16) |
                     (static_cast<uint32_t>(data[pos + 2]) << 8) |
                     static_cast<uint32_t>(data[pos + 3]);
        pos += 4;
        return v;
    };
    if (readU4() != CLASS_MAGIC) return;
    readU2(); readU2();
    uint16_t cpCount = readU2();
    std::vector<std::string> cpStrings(cpCount);
    std::vector<uint16_t> cpClasses(cpCount);

    for (uint16_t i = 1; i < cpCount; i++) {
        if (pos >= size) return;
        uint8_t tag = data[pos++];
        switch (tag) {
            case CONSTANT_Utf8: {
                uint16_t len = readU2();
                if (pos + len > size) return;
                cpStrings[i] = std::string(reinterpret_cast<const char*>(&data[pos]), len);
                pos += len;
                break;
            }
            case CONSTANT_Integer: case CONSTANT_Float: pos += 4; break;
            case CONSTANT_Long: case CONSTANT_Double: pos += 8; i++; break;
            case CONSTANT_Class: cpClasses[i] = readU2(); break;
            case CONSTANT_String: case CONSTANT_MethodType:
            case CONSTANT_Module: case CONSTANT_Package: pos += 2; break;
            case CONSTANT_FieldRef: case CONSTANT_MethodRef:
            case CONSTANT_InterfaceMethodRef: case CONSTANT_NameAndType:
            case CONSTANT_Dynamic: case CONSTANT_InvokeDynamic: pos += 4; break;
            case CONSTANT_MethodHandle: pos += 3; break;
            default: return;
        }
    }
    readU2();
    readU2();
    uint16_t superClassIdx = readU2();
    if (superClassIdx > 0 && superClassIdx < cpCount && cpClasses[superClassIdx] < cpCount) {
        entry.superClass = cpStrings[cpClasses[superClassIdx]];
        std::replace(entry.superClass.begin(), entry.superClass.end(), '/', '.');
    }
    uint16_t interfacesCount = readU2();
    for (uint16_t i = 0; i < interfacesCount; i++) {
        uint16_t ifaceIdx = readU2();
        if (ifaceIdx < cpCount && cpClasses[ifaceIdx] < cpCount) {
            std::string iface = cpStrings[cpClasses[ifaceIdx]];
            std::replace(iface.begin(), iface.end(), '/', '.');
            entry.interfaces.insert(iface);
            if (iface == "net.fabricmc.api.ModInitializer")       entry.isModInitializer = true;
            else if (iface == "net.fabricmc.api.ClientModInitializer") entry.isClientModInitializer = true;
        }
    }



    if (pos + 2 > size) return;
    uint16_t fieldsCount = readU2();
    for (uint16_t i = 0; i < fieldsCount; i++) {
        if (pos + 8 > size) return;
        pos += 6;
        uint16_t attrCount = readU2();
        for (uint16_t a = 0; a < attrCount; a++) {
            if (pos + 6 > size) return;
            pos += 2;
            uint32_t attrLen = readU4();
            if (pos + attrLen > size) return;
            pos += attrLen;
        }
    }
}



void Jar::scanNativeMethods(const JarEntry& entry, std::vector<NativeMethodInfo>& out) {
    if (!entry.isClass || entry.data.size() < 10) return;
    const uint8_t* data = entry.data.data();
    size_t size = entry.data.size();
    size_t pos = 0;

    auto readU2 = [&]() -> uint16_t {
        if (pos + 2 > size) return 0;
        uint16_t v = static_cast<uint16_t>((data[pos] << 8) | data[pos + 1]);
        pos += 2; return v;
    };
    auto readU4 = [&]() -> uint32_t {
        if (pos + 4 > size) return 0;
        uint32_t v = (static_cast<uint32_t>(data[pos]) << 24) |
                     (static_cast<uint32_t>(data[pos+1]) << 16) |
                     (static_cast<uint32_t>(data[pos+2]) << 8) |
                     static_cast<uint32_t>(data[pos+3]);
        pos += 4; return v;
    };

    if (readU4() != CLASS_MAGIC) return;
    readU2(); readU2();

    uint16_t cpCount = readU2();
    std::vector<std::string> cpStrings(cpCount);
    for (uint16_t i = 1; i < cpCount; i++) {
        if (pos >= size) return;
        uint8_t tag = data[pos++];
        switch (tag) {
            case CONSTANT_Utf8: {
                uint16_t len = readU2();
                if (pos + len > size) return;
                cpStrings[i] = std::string(reinterpret_cast<const char*>(&data[pos]), len);
                pos += len; break;
            }
            case CONSTANT_Integer: case CONSTANT_Float: pos += 4; break;
            case CONSTANT_Long: case CONSTANT_Double: pos += 8; i++; break;
            case CONSTANT_Class: pos += 2; break;
            case CONSTANT_String: case CONSTANT_MethodType:
            case CONSTANT_Module: case CONSTANT_Package: pos += 2; break;
            case CONSTANT_FieldRef: case CONSTANT_MethodRef:
            case CONSTANT_InterfaceMethodRef: case CONSTANT_NameAndType:
            case CONSTANT_Dynamic: case CONSTANT_InvokeDynamic: pos += 4; break;
            case CONSTANT_MethodHandle: pos += 3; break;
            default: return;
        }
    }

    pos += 2; pos += 2; pos += 2;
    uint16_t ifCount = readU2();
    pos += ifCount * 2;

    if (pos + 2 > size) return;
    uint16_t fCount = readU2();
    for (uint16_t i = 0; i < fCount; i++) {
        if (pos + 8 > size) return;
        pos += 6;
        uint16_t ac = readU2();
        for (uint16_t a = 0; a < ac; a++) {
            if (pos + 6 > size) return;
            pos += 2; uint32_t al = readU4();
            if (pos + al > size) return;
            pos += al;
        }
    }

    if (pos + 2 > size) return;
    uint16_t mCount = readU2();
    std::string ownerInternal = entry.name;
    std::replace(ownerInternal.begin(), ownerInternal.end(), '.', '/');

    for (uint16_t i = 0; i < mCount; i++) {
        if (pos + 8 > size) return;
        uint16_t accessFlags = readU2();
        uint16_t nameIdx     = readU2();
        uint16_t descIdx     = readU2();
        uint16_t attrCount   = readU2();
        bool isNative = (accessFlags & 0x0100) != 0;
        std::string methodName = (nameIdx < cpCount) ? cpStrings[nameIdx] : "";
        std::string descriptor = (descIdx < cpCount) ? cpStrings[descIdx] : "";
        for (uint16_t a = 0; a < attrCount; a++) {
            if (pos + 6 > size) return;
            pos += 2; uint32_t al = readU4();
            if (pos + al > size) return;
            pos += al;
        }
        if (isNative && !methodName.empty() && !descriptor.empty()) {
            NativeMethodInfo nm;
            nm.ownerClass  = ownerInternal;
            nm.methodName  = methodName;
            nm.descriptor  = descriptor;
            out.push_back(std::move(nm));
        }
    }
}

std::vector<NativeMethodInfo> Jar::getNativeMethods() {
    buildCache();
    std::vector<NativeMethodInfo> result;
    for (auto& e : cache)
        if (e.isClass) scanNativeMethods(e, result);
    return result;
}


void Jar::sortClassesByDependency() {
    std::map<std::string, JarEntry*> classMap;
    for (auto& e : cache)
        if (e.isClass) classMap[e.name] = &e;
    std::map<std::string, int> priorities;
    std::function<int(const std::string&)> calcPriority = [&](const std::string& name) -> int {
        if (priorities.count(name)) return priorities[name];
        auto it = classMap.find(name);
        if (it == classMap.end()) return 0;
        int maxDep = 0;
        JarEntry* entry = it->second;
        if (!entry->superClass.empty() && entry->superClass != "java.lang.Object")
            maxDep = std::max(maxDep, calcPriority(entry->superClass) + 1);
        for (const auto& iface : entry->interfaces)
            maxDep = std::max(maxDep, calcPriority(iface) + 1);
        priorities[name] = maxDep;
        return maxDep;
    };
    for (auto& e : cache)
        if (e.isClass) e.loadPriority = calcPriority(e.name);
    std::stable_sort(cache.begin(), cache.end(), [](const JarEntry& a, const JarEntry& b) {
        if (a.isClass != b.isClass) return a.isClass > b.isClass;
        return a.loadPriority < b.loadPriority;
    });
}


static std::string jsonString(const std::string& json, const std::string& key) {
    std::string k = "\"" + key + "\"";
    size_t pos = json.find(k);
    if (pos == std::string::npos) return "";
    pos = json.find(':', pos + k.size());
    if (pos == std::string::npos) return "";
    pos = json.find('"', pos);
    if (pos == std::string::npos) return "";
    size_t end = json.find('"', pos + 1);
    if (end == std::string::npos) return "";
    return json.substr(pos + 1, end - pos - 1);
}

static bool jsonBool(const std::string& json, const std::string& key) {
    std::string k = "\"" + key + "\"";
    size_t pos = json.find(k);
    if (pos == std::string::npos) return false;
    pos = json.find(':', pos + k.size());
    if (pos == std::string::npos) return false;
    while (pos < json.size() && (json[pos] == ':' || json[pos] == ' ' || json[pos] == '\t' || json[pos] == '\n' || json[pos] == '\r')) pos++;
    return json.substr(pos, 4) == "true";
}

static int jsonInt(const std::string& json, const std::string& key, int def = 0) {
    std::string k = "\"" + key + "\"";
    size_t pos = json.find(k);
    if (pos == std::string::npos) return def;
    pos = json.find(':', pos + k.size());
    if (pos == std::string::npos) return def;
    while (pos < json.size() && !isdigit(json[pos])) pos++;
    if (pos >= json.size()) return def;
    return std::stoi(json.substr(pos));
}

static std::vector<std::string> jsonStringArray(const std::string& json, const std::string& key) {
    std::vector<std::string> result;
    std::string k = "\"" + key + "\"";
    size_t pos = json.find(k);
    if (pos == std::string::npos) return result;
    pos = json.find('[', pos + k.size());
    if (pos == std::string::npos) return result;
    size_t end = json.find(']', pos);
    if (end == std::string::npos) return result;
    std::string arr = json.substr(pos + 1, end - pos - 1);
    size_t p = 0;
    while ((p = arr.find('"', p)) != std::string::npos) {
        size_t e = arr.find('"', p + 1);
        if (e == std::string::npos) break;
        std::string val = arr.substr(p + 1, e - p - 1);
        if (!val.empty()) result.push_back(val);
        p = e + 1;
    }
    return result;
}


MixinConfigInfo Jar::parseMixinConfigJson(const std::string& configPath,
                                           const std::vector<uint8_t>& data,
                                           int orderIndex,
                                           bool fromFabricModJson) {
    MixinConfigInfo info;
    info.configPath = configPath;
    info.orderIndex = orderIndex;
    info.fromFabricModJson = fromFabricModJson;
    info.fromFileScan = !fromFabricModJson;
    info.rawJsonBytes = data;

    std::string json(data.begin(), data.end());

    info.packageName       = jsonString(json, "package");
    info.refmap            = jsonString(json, "refmap");
    info.plugin            = jsonString(json, "plugin");
    info.required          = jsonBool(json, "required");
    info.minVersion        = jsonString(json, "minVersion");
    info.compatibilityLevel= jsonString(json, "compatibilityLevel");


    size_t injPos = json.find("\"injectors\"");
    if (injPos != std::string::npos) {
        size_t brace = json.find('{', injPos);
        if (brace != std::string::npos) {
            size_t end = json.find('}', brace);
            if (end != std::string::npos) {
                std::string injBlock = json.substr(brace, end - brace + 1);
                info.injectors.defaultRequire = jsonInt(injBlock, "defaultRequire", 0);
            }
        }
    }


    size_t owPos = json.find("\"overwrites\"");
    if (owPos != std::string::npos) {
        size_t brace = json.find('{', owPos);
        if (brace != std::string::npos) {
            size_t end = json.find('}', brace);
            if (end != std::string::npos) {
                std::string owBlock = json.substr(brace, end - brace + 1);
                info.overwrites.requireAnnotations = jsonBool(owBlock, "requireAnnotations");
            }
        }
    }



    auto parseArrayWithObjects = [&](const std::string& key) -> std::vector<std::string> {
        std::vector<std::string> result;
        std::string k = "\"" + key + "\"";
        size_t pos = json.find(k);
        if (pos == std::string::npos) return result;
        pos = json.find('[', pos + k.size());
        if (pos == std::string::npos) return result;

        int depth = 1;
        size_t scan = pos + 1;
        while (scan < json.size() && depth > 0) {
            if (json[scan] == '[') depth++;
            else if (json[scan] == ']') depth--;
            scan++;
        }
        if (depth != 0) return result;
        std::string arr = json.substr(pos + 1, scan - pos - 2);

        size_t p = 0;
        while (p < arr.size()) {
            while (p < arr.size() && arr[p] != '"' && arr[p] != '{') p++;
            if (p >= arr.size()) break;
            if (arr[p] == '"') {
                p++;
                size_t e = arr.find('"', p);
                if (e == std::string::npos) break;
                std::string val = arr.substr(p, e - p);
                if (!val.empty()) result.push_back(val);
                p = e + 1;
            } else {

                size_t endObj = arr.find('}', p);
                if (endObj == std::string::npos) break;
                std::string obj = arr.substr(p, endObj - p + 1);
                std::string name = jsonString(obj, "name");
                if (!name.empty()) result.push_back(name);
                p = endObj + 1;
            }
        }
        return result;
    };

    info.commonMixins = parseArrayWithObjects("mixins");
    info.clientMixins = parseArrayWithObjects("client");
    info.serverMixins = parseArrayWithObjects("server");

    return info;
}


std::vector<MixinConfigInfo> Jar::getMixinConfigInfos() {
    std::vector<MixinConfigInfo> result;
    int orderIndex = 0;


    std::vector<std::string> configNames;
    for (auto& zf : files) {
        if (zf.name != "fabric.mod.json") continue;
        auto data = readEntry(zf);
        if (data.empty()) continue;
        std::string json(data.begin(), data.end());
        configNames = jsonStringArray(json, "mixins");
        break;
    }


    for (const auto& name : configNames) {
        for (auto& zf : files) {
            if (zf.name != name) continue;
            auto data = readEntry(zf);
            if (data.empty()) continue;
            result.push_back(parseMixinConfigJson(name, data, orderIndex++, true));
            break;
        }
    }



    std::set<std::string> seen;
    for (const auto& r : result) seen.insert(r.configPath);
    for (auto& zf : files) {
        if (zf.name.back() == '/') continue;
        std::string lower = zf.name;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        if (lower.find("mixin") == std::string::npos) continue;
        if (lower.find(".json") == std::string::npos) continue;
        if (seen.count(zf.name)) continue;
        auto data = readEntry(zf);
        if (data.empty()) continue;
        result.push_back(parseMixinConfigJson(zf.name, data, orderIndex++, false));
    }

    return result;
}


std::vector<std::string> Jar::getMixinConfigs() {
    auto infos = getMixinConfigInfos();
    std::vector<std::string> result;
    for (const auto& i : infos) result.push_back(i.configPath);
    return result;
}


std::vector<RefmapInfo> Jar::getRefmaps() {
    std::vector<RefmapInfo> result;

    auto configs = getMixinConfigInfos();
    std::set<std::string> needed;
    for (const auto& c : configs)
        if (!c.refmap.empty()) needed.insert(c.refmap);

    for (auto& zf : files) {
        if (zf.name.back() == '/') continue;
        bool isNeeded = needed.count(zf.name) > 0;

        std::string lower = zf.name;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        bool looksLikeRefmap = (lower.find("refmap") != std::string::npos &&
                                lower.find(".json") != std::string::npos);
        if (!isNeeded && !looksLikeRefmap) continue;
        auto data = readEntry(zf);
        if (data.empty()) continue;
        RefmapInfo ri;
        ri.resourcePath = zf.name;
        ri.rawBytes = std::move(data);
        result.push_back(std::move(ri));
    }
    return result;
}


std::string Jar::parseAccessWidenerNamespace(const std::vector<uint8_t>& data) {

    std::string text(data.begin(), std::min(data.begin() + 256, data.end()));
    size_t pos = 0;

    while (pos < text.size() && text[pos] != ' ' && text[pos] != '\t') pos++;

    while (pos < text.size() && (text[pos] == ' ' || text[pos] == '\t')) pos++;

    while (pos < text.size() && text[pos] != ' ' && text[pos] != '\t') pos++;

    while (pos < text.size() && (text[pos] == ' ' || text[pos] == '\t')) pos++;

    size_t nsStart = pos;
    while (pos < text.size() && text[pos] != ' ' && text[pos] != '\t' &&
           text[pos] != '\n' && text[pos] != '\r') pos++;
    return text.substr(nsStart, pos - nsStart);
}

std::vector<AccessWidenerInfo> Jar::getAccessWideners() {
    std::vector<AccessWidenerInfo> result;

    std::string awFromMeta;
    for (auto& zf : files) {
        if (zf.name != "fabric.mod.json") continue;
        auto data = readEntry(zf);
        if (data.empty()) continue;
        std::string json(data.begin(), data.end());
        awFromMeta = jsonString(json, "accessWidener");
        break;
    }

    for (auto& zf : files) {
        if (zf.name.back() == '/') continue;
        std::string lower = zf.name;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        bool byMeta = (!awFromMeta.empty() && zf.name == awFromMeta);
        bool byScan = (lower.size() > 12 &&
                       lower.substr(lower.size() - 12) == ".accesswidener");
        if (!byMeta && !byScan) continue;
        auto data = readEntry(zf);
        if (data.empty()) continue;
        AccessWidenerInfo ai;
        ai.resourcePath = zf.name;
        ai.rawBytes     = data;
        ai.namespace_   = parseAccessWidenerNamespace(data);
        result.push_back(std::move(ai));
    }
    return result;
}


std::vector<FabricEntrypoint> Jar::getFabricEntrypoints() {
    std::vector<FabricEntrypoint> result;
    for (auto& zf : files) {
        if (zf.name != "fabric.mod.json") continue;
        auto data = readEntry(zf);
        if (data.empty()) continue;
        std::string json(data.begin(), data.end());

        auto parseGroup = [&](const std::string& type) {
            std::string key = "\"" + type + "\"";
            size_t pos = json.find("\"entrypoints\"");
            if (pos == std::string::npos) return;
            pos = json.find(key, pos);
            if (pos == std::string::npos) return;
            pos = json.find('[', pos);
            if (pos == std::string::npos) return;
            size_t end = json.find(']', pos);
            if (end == std::string::npos) return;
            std::string arr = json.substr(pos + 1, end - pos - 1);
            size_t p = 0;
            while (p < arr.size()) {
                while (p < arr.size() && arr[p] != '"' && arr[p] != '{') p++;
                if (p >= arr.size()) break;
                if (arr[p] == '"') {
                    p++;
                    size_t e = arr.find('"', p);
                    if (e == std::string::npos) break;
                    std::string cls = arr.substr(p, e - p);
                    if (!cls.empty() && cls.find(':') == std::string::npos) {
                        result.push_back({cls, type, ""});
                    }
                    p = e + 1;
                } else {

                    size_t endObj = arr.find('}', p);
                    if (endObj == std::string::npos) break;
                    std::string obj = arr.substr(p, endObj - p + 1);
                    std::string val = jsonString(obj, "value");
                    std::string adapter = jsonString(obj, "adapter");
                    if (!val.empty()) result.push_back({val, type, adapter});
                    p = endObj + 1;
                }
            }
        };

        parseGroup("main");
        parseGroup("client");
        parseGroup("server");


        size_t epPos = json.find("\"entrypoints\"");
        if (epPos != std::string::npos) {
            size_t brace = json.find('{', epPos);
            if (brace != std::string::npos) {
                size_t end = brace + 1;
                int depth = 1;
                while (end < json.size() && depth > 0) {
                    if (json[end] == '{') depth++;
                    else if (json[end] == '}') depth--;
                    end++;
                }
                std::string block = json.substr(brace + 1, end - brace - 2);


                size_t p = 0;
                int bdepth = 0;
                while (p < block.size()) {
                    char c = block[p];
                    if (c == '[' || c == '{') { bdepth++; p++; continue; }
                    if (c == ']' || c == '}') { bdepth--; p++; continue; }
                    if (c == '"' && bdepth == 0) {

                        size_t e = block.find('"', p + 1);
                        if (e == std::string::npos) break;
                        std::string k = block.substr(p + 1, e - p - 1);

                        size_t colon = block.find_first_not_of(" \t\r\n", e + 1);
                        if (colon != std::string::npos && block[colon] == ':') {

                            if (k != "main" && k != "client" && k != "server"
                                    && !k.empty()) {
                                parseGroup(k);
                            }
                        }
                        p = e + 1;
                    } else {
                        p++;
                    }
                }
            }
        }
        break;
    }
    return result;
}

std::string Jar::getMainClass() {
    auto eps = getFabricEntrypoints();
    for (auto& ep : eps) if (ep.type == "client") return ep.className;
    for (auto& ep : eps) if (ep.type == "main")   return ep.className;
    for (auto& ep : eps) if (ep.type == "server")  return ep.className;
    return "";
}

std::string Jar::getPlatform(std::string& version) {
    version = "";
    for (auto& zf : files) {
        if (zf.name == "fabric.mod.json") {
            auto data = readEntry(zf);
            if (!data.empty()) {
                std::string json(data.begin(), data.end());
                size_t pos = json.find("\"minecraft\"");
                if (pos != std::string::npos) {
                    pos = json.find(':', pos);
                    if (pos != std::string::npos) {
                        pos = json.find('"', pos);
                        if (pos != std::string::npos) {
                            size_t end = json.find('"', pos + 1);
                            if (end != std::string::npos) {
                                std::string ver = json.substr(pos + 1, end - pos - 1);
                                size_t i = 0;
                                while (i < ver.size() && (ver[i]=='~'||ver[i]=='^'||ver[i]=='>'||ver[i]=='<'||ver[i]=='='||ver[i]==' ')) i++;
                                if (i < ver.size()) version = ver.substr(i);
                            }
                        }
                    }
                }
            }
            return "Fabric";
        }
    }
    return "Unknown";
}

std::string Jar::getJdkVersion() { return "21"; }

bool Jar::isValidMod() {
    for (auto& zf : files) {
        if (zf.name == "fabric.mod.json") return true;
        if (zf.name == "quilt.mod.json")  return true;
        if (zf.name == "META-INF/mods.toml") return true;
        if (zf.name == "mcmod.info") return true;
    }
    return false;
}

bool Jar::hasMixins() {

    for (auto& zf : files) {
        if (zf.name == "fabric.mod.json") {
            auto data = readEntry(zf);
            if (!data.empty()) {
                std::string json(data.begin(), data.end());
                if (json.find("\"mixins\"") != std::string::npos)
                    return true;
            }
        }
    }

    for (auto& zf : files) {
        std::string lower = zf.name;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        if (lower.find("mixin") != std::string::npos &&
            lower.find(".json") != std::string::npos) return true;
    }
    return false;
}

std::vector<std::string> Jar::getSkippedClasses() {
    std::vector<std::string> skipped;
    for (auto& zf : files) {
        std::string lower = zf.name;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        if (lower.find(".class") == std::string::npos) continue;
        if (lower.back() == '/') continue;
        if (lower.find("mixin")       != std::string::npos) skipped.push_back(zf.name + " (mixin)");
        else if (lower.find("accessor") != std::string::npos) skipped.push_back(zf.name + " (accessor)");
        else if (lower.find("module-info") != std::string::npos) skipped.push_back(zf.name + " (module-info)");
        else if (lower.find("package-info") != std::string::npos) skipped.push_back(zf.name + " (package-info)");
    }
    return skipped;
}

int Jar::getClassCount() {
    int count = 0;
    for (auto& zf : files) {
        std::string lower = zf.name;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        if (lower.find(".class") != std::string::npos && !shouldSkip(zf.name)) count++;
    }
    return count;
}

int Jar::getMixinCount() {
    int count = 0;
    for (auto& zf : files) {
        std::string lower = zf.name;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        if (lower.find(".class") != std::string::npos &&
            lower.find("mixin") != std::string::npos) count++;
    }
    return count;
}

std::vector<JarEntry> Jar::getResources() {
    std::vector<JarEntry> result;
    for (auto& zf : files) {
        if (zf.name.empty() || zf.name.back() == '/') continue;
        std::string lower = zf.name;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        if (lower.find(".class") != std::string::npos) continue;
        if (zf.name == "META-INF/MANIFEST.MF") continue;
        auto data = readEntry(zf);
        if (data.empty()) continue;
        JarEntry entry;
        entry.name    = zf.name;
        entry.data    = std::move(data);
        entry.isClass = false;
        result.push_back(std::move(entry));
    }
    return result;
}
