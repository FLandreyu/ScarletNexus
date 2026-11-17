"""Read a flat IMAPI ISO/Joliet image without mounting it or running its files."""
import hashlib
import json
import pathlib
import sys


def digest(stream, size=None):
    result = hashlib.sha256()
    while size is None or size > 0:
        chunk = stream.read(1024 * 1024 if size is None else min(size, 1024 * 1024))
        if not chunk:
            if size:
                raise ValueError("Truncated file extent")
            break
        result.update(chunk)
        if size is not None:
            size -= len(chunk)
    return result.hexdigest()


def extent(record, limit):
    offset = int.from_bytes(record[2:6], "little") * 2048
    size = int.from_bytes(record[10:14], "little")
    if offset + size > limit:
        raise ValueError("ISO extent escapes the image")
    return offset, size


def verify(iso, media):
    expected = {file.name: file for file in media.iterdir() if file.is_file()}
    if any(not file.is_file() or file.is_symlink() for file in media.iterdir()):
        raise ValueError("Test media must be flat regular files")
    limit = iso.stat().st_size
    with iso.open("rb") as stream:
        descriptor = None
        joliet = False
        for sector in range(16, 64):
            stream.seek(sector * 2048)
            data = stream.read(2048)
            if data[1:6] != b"CD001" or data[6] != 1:
                raise ValueError("Invalid ISO9660 descriptor")
            if data[0] == 1 and descriptor is None:
                descriptor = data
            if data[0] == 2 and data[88:91] in (b"%/@", b"%/C", b"%/E"):
                descriptor, joliet = data, True
            if data[0] == 255:
                break
        if descriptor is None or not joliet:
            raise ValueError("Expected ISO9660 with Joliet names")
        offset, size = extent(descriptor[156:190], limit)
        if size > 1024 * 1024:
            raise ValueError("Unexpected root directory size")
        stream.seek(offset)
        directory = stream.read(size)
        files = {}
        index = 0
        while index < size:
            length = directory[index]
            if length == 0:
                index = (index // 2048 + 1) * 2048
                continue
            record = directory[index:index + length]
            index += length
            if len(record) < 34 or len(record) < 33 + record[32]:
                raise ValueError("Invalid directory record")
            raw_name = record[33:33 + record[32]]
            if raw_name in (b"\0", b"\1"):
                continue
            name = raw_name.decode("utf-16-be").removesuffix(";1")
            if name in files or name not in expected or record[25] & 2:
                raise ValueError("Unexpected ISO member: " + name)
            position, count = extent(record, limit)
            stream.seek(position)
            actual = digest(stream, count)
            with expected[name].open("rb") as source:
                if actual != digest(source):
                    raise ValueError("ISO member differs: " + name)
            files[name] = actual
        if set(files) != set(expected):
            raise ValueError("ISO file inventory differs")
    with iso.open("rb") as stream:
        iso_sha = digest(stream)
    return {"jolietFilesVerified": len(files), "isoSha256": iso_sha,
            "installationAndBusinessGates": "NOT_RUN"}


if __name__ == "__main__":
    print(json.dumps(verify(pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]))))
